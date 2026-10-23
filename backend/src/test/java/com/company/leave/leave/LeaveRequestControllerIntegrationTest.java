package com.company.leave.leave;

import javax.sql.DataSource;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@Testcontainers
@SpringBootTest
@ExtendWith(OutputCaptureExtension.class)
class LeaveRequestControllerIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DataSource dataSource;

    private JdbcTemplate jdbcTemplate;

    /** Seeded employee (id=5, manager_id=3). */
    private static final long EMPLOYEE_ID = 5L;
    private static final long MANAGER_ID = 3L;

    @BeforeEach
    void setUp() {
        jdbcTemplate = new JdbcTemplate(dataSource);
        dropNotificationFailureTrigger();
        jdbcTemplate.update("delete from active_sessions");
        jdbcTemplate.update(
                "update users set must_change_password = false where id in (?, ?)", EMPLOYEE_ID, MANAGER_ID);
    }

    @AfterEach
    void cleanUp() {
        dropNotificationFailureTrigger();
        // Notification and balance-ledger rows reference requests, so delete them first.
        jdbcTemplate.update("delete from notifications");
        jdbcTemplate.update("delete from balance_ledger");
        jdbcTemplate.update("delete from leave_requests");
        jdbcTemplate.update(
                "update users set must_change_password = false where id in (?, ?)", EMPLOYEE_ID, MANAGER_ID);
        jdbcTemplate.update("""
                insert into leave_balances (user_id, leave_type_id, total_days, reserved_days)
                select users.id,
                       leave_types.id,
                       case leave_types.name
                           when 'Vacation' then 20
                           when 'Sick' then 10
                           when 'Parental' then 30
                           when 'Sabbatical' then 0
                       end,
                       0
                from users
                cross join leave_types
                on conflict (user_id, leave_type_id) do update
                    set total_days = excluded.total_days,
                        reserved_days = 0
                """);
    }

    @Test
    void happyPath_thuToTue_creates4WorkingDaysRequest(CapturedOutput output) throws Exception {
        long vacationTypeId = leaveTypeId("Vacation");

        MvcResult result = mockMvc.perform(post("/requests")
                        .with(authenticatedAs(EMPLOYEE_ID, "EMPLOYEE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "leaveTypeId": %d,
                                    "startDate": "2026-10-29",
                                    "endDate": "2026-11-03",
                                    "notes": "Family trip"
                                }
                                """.formatted(vacationTypeId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.employeeId").value((int) EMPLOYEE_ID))
                .andExpect(jsonPath("$.approverId").value((int) MANAGER_ID))
                .andExpect(jsonPath("$.leaveTypeId").value((int) vacationTypeId))
                .andExpect(jsonPath("$.startDate").value("2026-10-29"))
                .andExpect(jsonPath("$.endDate").value("2026-11-03"))
                .andExpect(jsonPath("$.workingDays").value(4))
                .andExpect(jsonPath("$.notes").value("Family trip"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.submittedAt").exists())
                .andExpect(jsonPath("$.decidedAt").isEmpty())
                .andExpect(jsonPath("$.decisionComment").isEmpty())
                .andReturn();

        long requestId = ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.id")).longValue();

        // reserved_days updated
        Integer reservedDays = jdbcTemplate.queryForObject(
                "select reserved_days from leave_balances where user_id = ? and leave_type_id = ?",
                Integer.class, EMPLOYEE_ID, vacationTypeId);
        assertThat(reservedDays).isEqualTo(4);

        // balance_ledger row written
        Integer ledgerCount = jdbcTemplate.queryForObject(
                "select count(*) from balance_ledger where user_id = ? and leave_type_id = ? and kind = 'RESERVE' and request_id = ?",
                Integer.class, EMPLOYEE_ID, vacationTypeId, requestId);
        assertThat(ledgerCount).isEqualTo(1);

        // audit event written
        Integer auditCount = jdbcTemplate.queryForObject(
                "select count(*) from audit_events where entity = 'LEAVE_REQUEST' and action = 'LEAVE_REQUEST_SUBMITTED' and entity_id = ?",
                Integer.class, requestId);
        assertThat(auditCount).isEqualTo(1);

        Integer notificationCount = jdbcTemplate.queryForObject(
                "select count(*) from notifications where user_id = ? and request_id = ? and kind = 'SUBMITTED'",
                Integer.class, MANAGER_ID, requestId);
        assertThat(notificationCount).isEqualTo(1);
        String subject = jdbcTemplate.queryForObject(
                "select subject from notifications where user_id = ? and request_id = ?",
                String.class, MANAGER_ID, requestId);
        assertThat(subject).contains("Vacation", "2026-10-29", "2026-11-03", "PENDING");
        assertThat(output.getAll())
                .contains("Notification for user " + MANAGER_ID, "Working days: 4", "/requests/" + requestId);
    }

    @Test
    void notificationHistory_returnsOnlyTheAuthenticatedUsersRows() throws Exception {
        long vacationTypeId = leaveTypeId("Vacation");
        MvcResult result = mockMvc.perform(post("/requests")
                        .with(authenticatedAs(EMPLOYEE_ID, "EMPLOYEE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "leaveTypeId": %d,
                                    "startDate": "2026-10-29",
                                    "endDate": "2026-11-03"
                                }
                                """.formatted(vacationTypeId)))
                .andExpect(status().isCreated())
                .andReturn();
        long requestId = ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.id")).longValue();

        jdbcTemplate.update(
                "insert into notifications (user_id, request_id, kind, subject, sent_at) values (?, ?, 'SUBMITTED', ?, now())",
                EMPLOYEE_ID, requestId, "Employee-only notification");

        mockMvc.perform(get("/notifications").with(authenticatedAs(MANAGER_ID, "MANAGER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].userId").value((int) MANAGER_ID))
                .andExpect(jsonPath("$[0].requestId").value((int) requestId))
                .andExpect(jsonPath("$[0].kind").value("SUBMITTED"))
                .andExpect(jsonPath("$[0].subject").isNotEmpty())
                .andExpect(jsonPath("$[0].sentAt").isNotEmpty());
    }

    @Test
    void notificationHistory_requiresAuthentication() throws Exception {
        mockMvc.perform(get("/notifications"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void notificationWriteFailure_doesNotRollbackSubmittedRequest(CapturedOutput output) throws Exception {
        jdbcTemplate.execute("""
                create or replace function reject_test_notification() returns trigger as $$
                begin
                    raise exception 'intentional notification failure';
                end;
                $$ language plpgsql
                """);
        jdbcTemplate.execute("""
                create trigger reject_test_notification
                before insert on notifications
                for each row execute function reject_test_notification()
                """);

        long vacationTypeId = leaveTypeId("Vacation");
        MvcResult result = mockMvc.perform(post("/requests")
                        .with(authenticatedAs(EMPLOYEE_ID, "EMPLOYEE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "leaveTypeId": %d,
                                    "startDate": "2026-10-29",
                                    "endDate": "2026-11-03"
                                }
                                """.formatted(vacationTypeId)))
                .andExpect(status().isCreated())
                .andReturn();
        long requestId = ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.id")).longValue();

        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from leave_requests where id = ?", Integer.class, requestId)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "select reserved_days from leave_balances where user_id = ? and leave_type_id = ?",
                Integer.class, EMPLOYEE_ID, vacationTypeId)).isEqualTo(4);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from audit_events where entity_id = ? and action = 'LEAVE_REQUEST_SUBMITTED'",
                Integer.class, requestId)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from notifications where request_id = ?", Integer.class, requestId)).isZero();
        assertThat(output.getAll()).contains("Unable to record notification", "intentional notification failure");
    }

    @Test
    void happyPath_notesOmitted_createsRequestWithNullNotes() throws Exception {
        long vacationTypeId = leaveTypeId("Vacation");

        mockMvc.perform(post("/requests")
                        .with(authenticatedAs(EMPLOYEE_ID, "EMPLOYEE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "leaveTypeId": %d,
                                    "startDate": "2026-10-29",
                                    "endDate": "2026-11-03"
                                }
                                """.formatted(vacationTypeId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.notes").isEmpty())
                .andExpect(jsonPath("$.workingDays").value(4));
    }

    @Test
    void endBeforeStart_returns400() throws Exception {
        long vacationTypeId = leaveTypeId("Vacation");

        mockMvc.perform(post("/requests")
                        .with(authenticatedAs(EMPLOYEE_ID, "EMPLOYEE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "leaveTypeId": %d,
                                    "startDate": "2026-11-03",
                                    "endDate": "2026-10-29"
                                }
                                """.formatted(vacationTypeId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.detail").value("endDate must be on or after startDate"));
    }

    @Test
    void zeroWorkingDays_weekendOnly_returns400() throws Exception {
        long vacationTypeId = leaveTypeId("Vacation");

        // 2026-10-31 is Saturday, 2026-11-01 is Sunday
        mockMvc.perform(post("/requests")
                        .with(authenticatedAs(EMPLOYEE_ID, "EMPLOYEE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "leaveTypeId": %d,
                                    "startDate": "2026-10-31",
                                    "endDate": "2026-11-01"
                                }
                                """.formatted(vacationTypeId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.detail").value("Range yields zero working days"));
    }

    @Test
    void insufficientBalance_returns409() throws Exception {
        long vacationTypeId = leaveTypeId("Vacation");

        // Employee has 20 total, 0 reserved => 20 available. Request more than 20.
        // Use a long range of working days that exceeds 20.
        mockMvc.perform(post("/requests")
                        .with(authenticatedAs(EMPLOYEE_ID, "EMPLOYEE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "leaveTypeId": %d,
                                    "startDate": "2026-10-05",
                                    "endDate": "2026-11-06"
                                }
                                """.formatted(vacationTypeId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_BALANCE"))
                .andExpect(jsonPath("$.detail").value(
                        org.hamcrest.Matchers.containsString("requested=")))
                .andExpect(jsonPath("$.detail").value(
                        org.hamcrest.Matchers.containsString("available=")));

        // No request or ledger rows should exist
        assertThat(jdbcTemplate.queryForObject("select count(*) from leave_requests", Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("select count(*) from balance_ledger", Integer.class)).isZero();
    }

    @Test
    void existingPendingReservation_reducesAvailableBalance() throws Exception {
        long vacationTypeId = leaveTypeId("Vacation");

        // Pre-reserve 10 days (simulating an earlier pending request)
        jdbcTemplate.update(
                "update leave_balances set reserved_days = 10 where user_id = ? and leave_type_id = ?",
                EMPLOYEE_ID, vacationTypeId);

        // Request 15 days — only 10 available (20 total - 10 reserved)
        mockMvc.perform(post("/requests")
                        .with(authenticatedAs(EMPLOYEE_ID, "EMPLOYEE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "leaveTypeId": %d,
                                    "startDate": "2026-10-05",
                                    "endDate": "2026-10-23"
                                }
                                """.formatted(vacationTypeId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_BALANCE"))
                .andExpect(jsonPath("$.detail").value(
                        org.hamcrest.Matchers.containsString("available=10")));
    }

    @Test
    void invalidLeaveType_returns400() throws Exception {
        mockMvc.perform(post("/requests")
                        .with(authenticatedAs(EMPLOYEE_ID, "EMPLOYEE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "leaveTypeId": 9999,
                                    "startDate": "2026-10-29",
                                    "endDate": "2026-11-03"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void unauthenticatedCallerGets401() throws Exception {
        mockMvc.perform(post("/requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "leaveTypeId": 1,
                                    "startDate": "2026-10-29",
                                    "endDate": "2026-11-03"
                                }
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void callerWhoMustChangePasswordGets403() throws Exception {
        jdbcTemplate.update("update users set must_change_password = true where id = ?", EMPLOYEE_ID);

        mockMvc.perform(post("/requests")
                        .with(authenticatedAs(EMPLOYEE_ID, "EMPLOYEE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "leaveTypeId": 1,
                                    "startDate": "2026-10-29",
                                    "endDate": "2026-11-03"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_REQUIRED"));
    }

    @Test
    void reservedDaysUpdatedCorrectly() throws Exception {
        long vacationTypeId = leaveTypeId("Vacation");

        // Submit first request: 4 working days
        mockMvc.perform(post("/requests")
                        .with(authenticatedAs(EMPLOYEE_ID, "EMPLOYEE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "leaveTypeId": %d,
                                    "startDate": "2026-10-29",
                                    "endDate": "2026-11-03"
                                }
                                """.formatted(vacationTypeId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.workingDays").value(4));

        Integer reservedAfterFirst = jdbcTemplate.queryForObject(
                "select reserved_days from leave_balances where user_id = ? and leave_type_id = ?",
                Integer.class, EMPLOYEE_ID, vacationTypeId);
        assertThat(reservedAfterFirst).isEqualTo(4);

        // Submit second request: 5 working days (Mon-Fri)
        mockMvc.perform(post("/requests")
                        .with(authenticatedAs(EMPLOYEE_ID, "EMPLOYEE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "leaveTypeId": %d,
                                    "startDate": "2026-11-09",
                                    "endDate": "2026-11-13"
                                }
                                """.formatted(vacationTypeId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.workingDays").value(5));

        Integer reservedAfterSecond = jdbcTemplate.queryForObject(
                "select reserved_days from leave_balances where user_id = ? and leave_type_id = ?",
                Integer.class, EMPLOYEE_ID, vacationTypeId);
        assertThat(reservedAfterSecond).isEqualTo(9);

        // Two ledger rows
        Integer ledgerCount = jdbcTemplate.queryForObject(
                "select count(*) from balance_ledger where user_id = ? and kind = 'RESERVE'",
                Integer.class, EMPLOYEE_ID);
        assertThat(ledgerCount).isEqualTo(2);
    }

    @Test
    void hrAdminWithNullManager_approvesOwnRequest() throws Exception {
        long hrAdminId = 1L;
        long vacationTypeId = leaveTypeId("Vacation");

        jdbcTemplate.update("delete from active_sessions");
        jdbcTemplate.update("update users set must_change_password = false where id = ?", hrAdminId);

        mockMvc.perform(post("/requests")
                        .with(authenticatedAs(hrAdminId, "HR_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "leaveTypeId": %d,
                                    "startDate": "2026-10-29",
                                    "endDate": "2026-11-03"
                                }
                                """.formatted(vacationTypeId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.employeeId").value((int) hrAdminId))
                .andExpect(jsonPath("$.approverId").value((int) hrAdminId))
                .andExpect(jsonPath("$.workingDays").value(4))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void noBalanceRow_returns409() throws Exception {
        long employeeId = EMPLOYEE_ID;
        long vacationTypeId = leaveTypeId("Vacation");

        // Remove the balance row so reserve() hits the orElseThrow path
        jdbcTemplate.update("delete from leave_balances where user_id = ? and leave_type_id = ?",
                employeeId, vacationTypeId);

        mockMvc.perform(post("/requests")
                        .with(authenticatedAs(employeeId, "EMPLOYEE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "leaveTypeId": %d,
                                    "startDate": "2026-10-29",
                                    "endDate": "2026-11-03"
                                }
                                """.formatted(vacationTypeId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_BALANCE"))
                .andExpect(jsonPath("$.detail").value(
                        org.hamcrest.Matchers.containsString("No balance row exists")));

        // No request should have been persisted (rolled back)
        assertThat(jdbcTemplate.queryForObject("select count(*) from leave_requests", Integer.class)).isZero();
    }

    private long leaveTypeId(String name) {
        return jdbcTemplate.queryForObject("select id from leave_types where name = ?", Long.class, name);
    }

    private void dropNotificationFailureTrigger() {
        jdbcTemplate.execute("drop trigger if exists reject_test_notification on notifications");
        jdbcTemplate.execute("drop function if exists reject_test_notification()");
    }

    private RequestPostProcessor authenticatedAs(long userId, String role) {
        return authentication(new TestingAuthenticationToken(
                userId, null, new SimpleGrantedAuthority("ROLE_" + role)));
    }
}
