package com.company.leave.leave;

import java.sql.Connection;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
    private static final long OTHER_MANAGER_ID = 4L;

    @BeforeEach
    void setUp() {
        jdbcTemplate = new JdbcTemplate(dataSource);
        dropNotificationFailureTrigger();
        dropEditAuditFailureTrigger();
        dropCancelAuditFailureTrigger();
        jdbcTemplate.update("delete from active_sessions");
        jdbcTemplate.update(
                "update users set must_change_password = false where id in (?, ?)", EMPLOYEE_ID, MANAGER_ID);
    }

    @AfterEach
    void cleanUp() {
        dropNotificationFailureTrigger();
        dropEditAuditFailureTrigger();
        dropCancelAuditFailureTrigger();
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
    void requestList_filtersStatusAndOnlyReturnsEmployeeOwnRows() throws Exception {
        long vacationTypeId = leaveTypeId("Vacation");
        long otherEmployeeId = 9L;
        insertRequest(EMPLOYEE_ID, MANAGER_ID, vacationTypeId, "PENDING");
        insertRequest(EMPLOYEE_ID, MANAGER_ID, vacationTypeId, "APPROVED");
        insertRequest(EMPLOYEE_ID, MANAGER_ID, vacationTypeId, "REJECTED");
        insertRequest(EMPLOYEE_ID, MANAGER_ID, vacationTypeId, "CANCELLED");
        insertRequest(otherEmployeeId, 4L, vacationTypeId, "PENDING");

        for (String requestStatus : new String[] {"PENDING", "APPROVED", "REJECTED", "CANCELLED"}) {
            mockMvc.perform(get("/requests")
                            .param("status", requestStatus)
                            .with(authenticatedAs(EMPLOYEE_ID, "EMPLOYEE")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].employeeId").value((int) EMPLOYEE_ID))
                    .andExpect(jsonPath("$[0].status").value(requestStatus));
        }

        mockMvc.perform(get("/requests").with(authenticatedAs(EMPLOYEE_ID, "EMPLOYEE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4));
    }

    @Test
    void managerRequestListIncludesOwnAndDirectReportsAndCanFilterReport() throws Exception {
        long vacationTypeId = leaveTypeId("Vacation");
        insertRequest(MANAGER_ID, 1L, vacationTypeId, "PENDING");
        insertRequest(EMPLOYEE_ID, MANAGER_ID, vacationTypeId, "PENDING");
        insertRequest(6L, MANAGER_ID, vacationTypeId, "APPROVED");
        insertRequest(9L, 4L, vacationTypeId, "PENDING");

        MvcResult allVisible = mockMvc.perform(get("/requests")
                        .with(authenticatedAs(MANAGER_ID, "MANAGER")))
                .andExpect(status().isOk())
                .andReturn();
        List<Integer> visibleEmployeeIds = JsonPath.read(allVisible.getResponse().getContentAsString(), "$[*].employeeId");
        assertThat(visibleEmployeeIds)
                .containsExactlyInAnyOrder((int) MANAGER_ID, (int) EMPLOYEE_ID, 6);

        mockMvc.perform(get("/requests")
                        .param("employeeId", Long.toString(EMPLOYEE_ID))
                        .with(authenticatedAs(MANAGER_ID, "MANAGER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].employeeId").value((int) EMPLOYEE_ID));

        mockMvc.perform(get("/requests")
                        .param("status", "APPROVED")
                        .with(authenticatedAs(MANAGER_ID, "MANAGER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].employeeId").value(6))
                .andExpect(jsonPath("$[0].status").value("APPROVED"));
    }

    @Test
    void managerCannotFilterRequestsForAnotherTeamsEmployee() throws Exception {
        mockMvc.perform(get("/requests")
                        .param("employeeId", "9")
                        .with(forwardedApiPrefix())
                        .with(authenticatedAs(MANAGER_ID, "MANAGER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from access_log where actor_id = ? and resource = ? and allowed = false",
                Integer.class, MANAGER_ID, "GET /api/requests")).isEqualTo(1);
    }

    @Test
    void hrCanFilterRequestsForAnyEmployee() throws Exception {
        long vacationTypeId = leaveTypeId("Vacation");
        insertRequest(9L, 4L, vacationTypeId, "PENDING");

        mockMvc.perform(get("/requests")
                        .param("employeeId", "9")
                        .with(authenticatedAs(1L, "HR_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].employeeId").value(9));
    }

    @Test
    void requestDetailIsVisibleToOwnerManagerAndHr() throws Exception {
        long requestId = insertRequest(EMPLOYEE_ID, MANAGER_ID, leaveTypeId("Vacation"), "PENDING");

        for (long userId : new long[] {EMPLOYEE_ID, MANAGER_ID, 1L}) {
            String role = userId == EMPLOYEE_ID ? "EMPLOYEE" : userId == MANAGER_ID ? "MANAGER" : "HR_ADMIN";
            mockMvc.perform(get("/requests/{requestId}", requestId)
                            .with(authenticatedAs(userId, role)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value((int) requestId))
                    .andExpect(jsonPath("$.employeeId").value((int) EMPLOYEE_ID))
                    .andExpect(jsonPath("$.status").value("PENDING"));
        }
    }

    @Test
    void employeeCannotReadAnotherEmployeesRequestAndDenialIsLogged() throws Exception {
        long requestId = insertRequest(EMPLOYEE_ID, MANAGER_ID, leaveTypeId("Vacation"), "PENDING");

        mockMvc.perform(get("/requests/{requestId}", requestId)
                        .with(forwardedApiPrefix())
                        .with(authenticatedAs(6L, "EMPLOYEE")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from access_log where actor_id = ? and resource = ? and allowed = false",
                Integer.class, 6L, "GET /api/requests/" + requestId)).isEqualTo(1);
    }

    @Test
    void requestDetailForUnknownIdReturnsNotFound() throws Exception {
        mockMvc.perform(get("/requests/{requestId}", Long.MAX_VALUE)
                        .with(authenticatedAs(EMPLOYEE_ID, "EMPLOYEE")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void requestHistoryShowsSubmissionEditsAndCancellationOldestFirst() throws Exception {
        long vacationTypeId = leaveTypeId("Vacation");
        long requestId = createPendingRequest(
                vacationTypeId, "2026-10-29", "2026-11-03", "initial notes");

        assertThat(performEdit(
                requestId, vacationTypeId, "2026-10-30", "2026-11-03", "first edit")).isEqualTo(200);
        assertThat(performEdit(
                requestId, vacationTypeId, "2026-11-02", "2026-11-03", "second edit")).isEqualTo(200);
        mockMvc.perform(post("/requests/{requestId}/cancel", requestId)
                        .with(authenticatedAs(EMPLOYEE_ID, "EMPLOYEE")))
                .andExpect(status().isOk());

        MvcResult result = mockMvc.perform(get("/requests/{requestId}/history", requestId)
                        .with(authenticatedAs(EMPLOYEE_ID, "EMPLOYEE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[0].type").value("SUBMITTED"))
                .andExpect(jsonPath("$[0].actorId").value((int) EMPLOYEE_ID))
                .andExpect(jsonPath("$[0].actorUsername").value("emp.jsmith"))
                .andExpect(jsonPath("$[0].occurredAt").isNotEmpty())
                .andExpect(jsonPath("$[0].detail")
                        .value("Submitted Vacation, 2026-10-29 to 2026-11-03 (4 working days)."))
                .andExpect(jsonPath("$[1].type").value("EDITED"))
                .andExpect(jsonPath("$[1].detail").value(
                        "Edited request: Changed dates from 2026-10-29 to 2026-11-03 (4 working days) "
                                + "to 2026-10-30 to 2026-11-03 (3 working days); updated notes."))
                .andExpect(jsonPath("$[2].type").value("EDITED"))
                .andExpect(jsonPath("$[2].detail").value(
                        "Edited request: Changed dates from 2026-10-30 to 2026-11-03 (3 working days) "
                                + "to 2026-11-02 to 2026-11-03 (2 working days); updated notes."))
                .andExpect(jsonPath("$[3].type").value("CANCELLED"))
                .andExpect(jsonPath("$[3].detail")
                        .value("Cancelled request for 2026-11-02 to 2026-11-03 (2 working days)."))
                .andReturn();

        List<String> occurredAt = JsonPath.read(result.getResponse().getContentAsString(), "$[*].occurredAt");
        assertThat(occurredAt.stream().map(Instant::parse).toList())
                .isSortedAccordingTo(java.util.Comparator.naturalOrder());
    }

    @Test
    void requestHistoryIsVisibleToOwnerDirectManagerAndHr() throws Exception {
        long requestId = createPendingRequest(
                leaveTypeId("Vacation"), "2026-10-29", "2026-11-03", "history access");

        for (long userId : new long[] {EMPLOYEE_ID, MANAGER_ID, 1L}) {
            String role = userId == EMPLOYEE_ID ? "EMPLOYEE" : userId == MANAGER_ID ? "MANAGER" : "HR_ADMIN";
            mockMvc.perform(get("/requests/{requestId}/history", requestId)
                            .with(authenticatedAs(userId, role)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].type").value("SUBMITTED"));
        }
    }

    @Test
    void requestHistoryDenialIsLoggedWithoutReturningData() throws Exception {
        long requestId = insertRequest(EMPLOYEE_ID, MANAGER_ID, leaveTypeId("Vacation"), "PENDING");
        long unrelatedManagerId = jdbcTemplate.queryForObject(
                "select id from users where role = 'MANAGER' and id <> ? order by id limit 1",
                Long.class, MANAGER_ID);

        mockMvc.perform(get("/requests/{requestId}/history", requestId)
                        .with(forwardedApiPrefix())
                        .with(authenticatedAs(6L, "EMPLOYEE")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        mockMvc.perform(get("/requests/{requestId}/history", requestId)
                        .with(forwardedApiPrefix())
                        .with(authenticatedAs(unrelatedManagerId, "MANAGER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from access_log where actor_id = ? and resource = ? and allowed = false",
                Integer.class, 6L, "GET /api/requests/" + requestId + "/history")).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from access_log where actor_id = ? and resource = ? and allowed = false",
                Integer.class, unrelatedManagerId, "GET /api/requests/" + requestId + "/history")).isEqualTo(1);
    }

    @Test
    void outOfScopeManagerCannotReadRequestDetailOrHistory() throws Exception {
        long requestId = insertRequest(EMPLOYEE_ID, MANAGER_ID, leaveTypeId("Vacation"), "PENDING");
        jdbcTemplate.update("update leave_requests set notes = 'confidential leave notes' where id = ?", requestId);

        MvcResult detail = mockMvc.perform(get("/requests/{requestId}", requestId)
                        .with(forwardedApiPrefix())
                        .with(authenticatedAs(OTHER_MANAGER_ID, "MANAGER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"))
                .andReturn();
        MvcResult history = mockMvc.perform(get("/requests/{requestId}/history", requestId)
                        .with(forwardedApiPrefix())
                        .with(authenticatedAs(OTHER_MANAGER_ID, "MANAGER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"))
                .andReturn();

        assertThat(detail.getResponse().getContentAsString()).doesNotContain("confidential leave notes");
        assertThat(history.getResponse().getContentAsString()).doesNotContain("confidential leave notes");
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from access_log where actor_id = ? and resource = ? and allowed = false",
                Integer.class, OTHER_MANAGER_ID, "GET /api/requests/" + requestId)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from access_log where actor_id = ? and resource = ? and allowed = false",
                Integer.class, OTHER_MANAGER_ID, "GET /api/requests/" + requestId + "/history")).isEqualTo(1);
    }

    @Test
    void requestHistoryForUnknownIdReturnsNotFound() throws Exception {
        mockMvc.perform(get("/requests/{requestId}/history", Long.MAX_VALUE)
                        .with(authenticatedAs(EMPLOYEE_ID, "EMPLOYEE")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void requestHistoryExplainsMissingDetailsOnLegacyEvents() throws Exception {
        long requestId = insertRequest(EMPLOYEE_ID, MANAGER_ID, leaveTypeId("Vacation"), "PENDING");
        jdbcTemplate.update("""
                insert into audit_events (entity, entity_id, action, actor_id, details_json, created_at)
                values ('LEAVE_REQUEST', ?, 'LEAVE_REQUEST_SUBMITTED', ?, cast(null as jsonb), now())
                """, requestId, EMPLOYEE_ID);
        jdbcTemplate.update("""
                insert into audit_events (entity, entity_id, action, actor_id, details_json, created_at)
                values ('LEAVE_REQUEST', ?, 'LEAVE_REQUEST_EDITED', ?, '{}'::jsonb, now() + interval '1 second')
                """, requestId, EMPLOYEE_ID);

        mockMvc.perform(get("/requests/{requestId}/history", requestId)
                        .with(authenticatedAs(EMPLOYEE_ID, "EMPLOYEE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].detail").value("Event details were not recorded."))
                .andExpect(jsonPath("$[1].detail").value("Event details were not recorded."));
    }

    @Test
    void requestHistoryIncludesCommentAuditEventsByRequestId() throws Exception {
        long requestId = createPendingRequest(
                leaveTypeId("Vacation"), "2026-10-29", "2026-11-03", "history comment");
        jdbcTemplate.update("""
                insert into audit_events (entity, entity_id, action, actor_id, details_json, created_at)
                values (
                    'REQUEST_COMMENT',
                    ?,
                    'REQUEST_COMMENT_ADDED',
                    ?,
                    jsonb_build_object('requestId', ?, 'comment', ?),
                    now() + interval '1 second'
                )
                """, requestId + 1000, MANAGER_ID, requestId, "Please arrange coverage.");

        mockMvc.perform(get("/requests/{requestId}/history", requestId)
                        .with(authenticatedAs(EMPLOYEE_ID, "EMPLOYEE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[1].type").value("COMMENT"))
                .andExpect(jsonPath("$[1].actorId").value((int) MANAGER_ID))
                .andExpect(jsonPath("$[1].actorUsername").value("mgr.chen"))
                .andExpect(jsonPath("$[1].detail").value("Please arrange coverage."));
    }

    @Test
    void requestHistoryUsesStableAuditIdOrderForEqualTimestamps() throws Exception {
        long requestId = insertRequest(EMPLOYEE_ID, MANAGER_ID, leaveTypeId("Vacation"), "PENDING");
        jdbcTemplate.update("""
                insert into audit_events (entity, entity_id, action, actor_id, details_json, created_at)
                values ('LEAVE_REQUEST', ?, 'LEAVE_REQUEST_EDITED', ?, cast(null as jsonb), '2026-10-25T10:00:00Z'),
                       ('LEAVE_REQUEST', ?, 'LEAVE_REQUEST_SUBMITTED', ?, cast(null as jsonb), '2026-10-25T10:00:00Z')
                """, requestId, EMPLOYEE_ID, requestId, EMPLOYEE_ID);

        mockMvc.perform(get("/requests/{requestId}/history", requestId)
                        .with(authenticatedAs(EMPLOYEE_ID, "EMPLOYEE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].type").value("EDITED"))
                .andExpect(jsonPath("$[1].type").value("SUBMITTED"));
    }

    @Test
    void invalidRequestStatusReturnsValidationProblem() throws Exception {
        mockMvc.perform(get("/requests")
                        .param("status", "UNKNOWN")
                        .with(authenticatedAs(EMPLOYEE_ID, "EMPLOYEE")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
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

    @Test
    void ownerCanEditPendingRequestAndChangedReservationIsAudited() throws Exception {
        long vacationTypeId = leaveTypeId("Vacation");
        long requestId = createPendingRequest(vacationTypeId, "2026-10-29", "2026-11-03", "Family trip");
        int notificationsBefore = jdbcTemplate.queryForObject(
                "select count(*) from notifications where request_id = ?", Integer.class, requestId);
        Instant submittedAt = jdbcTemplate.queryForObject(
                "select submitted_at from leave_requests where id = ?", java.sql.Timestamp.class, requestId).toInstant();

        MvcResult editResult = mockMvc.perform(put("/requests/{requestId}", requestId)
                        .with(authenticatedAs(EMPLOYEE_ID, "EMPLOYEE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "leaveTypeId": %d,
                                    "startDate": "2026-10-30",
                                    "endDate": "2026-11-03",
                                    "notes": "Trip shortened"
                                }
                                """.formatted(vacationTypeId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value((int) requestId))
                .andExpect(jsonPath("$.employeeId").value((int) EMPLOYEE_ID))
                .andExpect(jsonPath("$.approverId").value((int) MANAGER_ID))
                .andExpect(jsonPath("$.leaveTypeId").value((int) vacationTypeId))
                .andExpect(jsonPath("$.startDate").value("2026-10-30"))
                .andExpect(jsonPath("$.endDate").value("2026-11-03"))
                .andExpect(jsonPath("$.workingDays").value(3))
                .andExpect(jsonPath("$.notes").value("Trip shortened"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.submittedAt").exists())
                .andExpect(jsonPath("$.decidedAt").isEmpty())
                .andExpect(jsonPath("$.decisionComment").isEmpty())
                .andReturn();
        assertThat(Instant.parse(JsonPath.read(editResult.getResponse().getContentAsString(), "$.submittedAt")))
                .isEqualTo(submittedAt);

        assertThat(jdbcTemplate.queryForObject(
                "select reserved_days from leave_balances where user_id = ? and leave_type_id = ?",
                Integer.class, EMPLOYEE_ID, vacationTypeId)).isEqualTo(3);
        assertThat(jdbcTemplate.queryForList(
                "select kind || ':' || change_days from balance_ledger where request_id = ? order by id",
                String.class, requestId)).containsExactly("RESERVE:4", "RELEASE:-4", "RESERVE:3");
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from audit_events where entity = 'LEAVE_REQUEST' "
                        + "and action = 'LEAVE_REQUEST_EDITED' and entity_id = ?",
                Integer.class, requestId)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from notifications where request_id = ?", Integer.class, requestId))
                .isEqualTo(notificationsBefore);
    }

    @Test
    void editingUnknownRequestReturnsNotFoundWithoutSideEffects() throws Exception {
        long vacationTypeId = leaveTypeId("Vacation");
        Integer reservedBefore = jdbcTemplate.queryForObject(
                "select reserved_days from leave_balances where user_id = ? and leave_type_id = ?",
                Integer.class, EMPLOYEE_ID, vacationTypeId);
        int ledgerBefore = jdbcTemplate.queryForObject("select count(*) from balance_ledger", Integer.class);
        int auditBefore = jdbcTemplate.queryForObject("select count(*) from audit_events", Integer.class);

        mockMvc.perform(put("/requests/{requestId}", Long.MAX_VALUE)
                        .with(authenticatedAs(EMPLOYEE_ID, "EMPLOYEE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody(vacationTypeId, "2026-10-30", "2026-10-30", "Missing")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        assertThat(jdbcTemplate.queryForObject(
                "select reserved_days from leave_balances where user_id = ? and leave_type_id = ?",
                Integer.class, EMPLOYEE_ID, vacationTypeId)).isEqualTo(reservedBefore);
        assertThat(jdbcTemplate.queryForObject("select count(*) from balance_ledger", Integer.class))
                .isEqualTo(ledgerBefore);
        assertThat(jdbcTemplate.queryForObject("select count(*) from audit_events", Integer.class))
                .isEqualTo(auditBefore);
    }

    @Test
    void simultaneousEditsKeepRequestAndReservationConsistent() throws Exception {
        long vacationTypeId = leaveTypeId("Vacation");
        long requestId = createPendingRequest(vacationTypeId, "2026-10-29", "2026-11-03", "Original");
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try (Connection balanceLock = dataSource.getConnection()) {
            balanceLock.setAutoCommit(false);
            try (var statement = balanceLock.prepareStatement(
                    "select reserved_days from leave_balances where user_id = ? and leave_type_id = ? for update")) {
                statement.setLong(1, EMPLOYEE_ID);
                statement.setLong(2, vacationTypeId);
                statement.executeQuery().close();
            }

            Future<Integer> first = executor.submit(() -> {
                ready.countDown();
                start.await();
                return performEdit(requestId, vacationTypeId, "2026-11-09", "2026-11-13", "Five days");
            });
            Future<Integer> second = executor.submit(() -> {
                ready.countDown();
                start.await();
                return performEdit(requestId, vacationTypeId, "2026-11-16", "2026-11-23", "Six days");
            });

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            awaitRequestLockWaiters();
            balanceLock.commit();

            assertThat(first.get(10, TimeUnit.SECONDS)).isEqualTo(200);
            assertThat(second.get(10, TimeUnit.SECONDS)).isEqualTo(200);
        } finally {
            executor.shutdownNow();
        }

        int requestDays = jdbcTemplate.queryForObject(
                "select working_days from leave_requests where id = ?", Integer.class, requestId);
        int reservedDays = jdbcTemplate.queryForObject(
                "select reserved_days from leave_balances where user_id = ? and leave_type_id = ?",
                Integer.class, EMPLOYEE_ID, vacationTypeId);
        int ledgerDays = jdbcTemplate.queryForObject(
                "select sum(change_days) from balance_ledger where request_id = ?", Integer.class, requestId);
        assertThat(reservedDays).isEqualTo(requestDays);
        assertThat(ledgerDays).isEqualTo(requestDays);
    }

    @Test
    void editWithSameWorkingDaysDoesNotWriteBalanceLedgerRows() throws Exception {
        long vacationTypeId = leaveTypeId("Vacation");
        long requestId = createPendingRequest(vacationTypeId, "2026-10-29", "2026-11-03", "Original");

        mockMvc.perform(put("/requests/{requestId}", requestId)
                        .with(authenticatedAs(EMPLOYEE_ID, "EMPLOYEE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "leaveTypeId": %d,
                                    "startDate": "2026-11-06",
                                    "endDate": "2026-11-11",
                                    "notes": "New dates, same number of days"
                                }
                                """.formatted(vacationTypeId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workingDays").value(4));

        assertThat(jdbcTemplate.queryForObject(
                "select reserved_days from leave_balances where user_id = ? and leave_type_id = ?",
                Integer.class, EMPLOYEE_ID, vacationTypeId)).isEqualTo(4);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from balance_ledger where request_id = ?", Integer.class, requestId)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from audit_events where entity_id = ? and action = 'LEAVE_REQUEST_EDITED'",
                Integer.class, requestId)).isEqualTo(1);
    }

    @Test
    void editCanUseItsOwnReservationPlusOtherAvailableDays() throws Exception {
        long vacationTypeId = leaveTypeId("Vacation");
        long requestId = createPendingRequest(vacationTypeId, "2026-10-29", "2026-11-03", "Original");
        jdbcTemplate.update(
                "update leave_balances set total_days = 6 where user_id = ? and leave_type_id = ?",
                EMPLOYEE_ID, vacationTypeId);

        mockMvc.perform(put("/requests/{requestId}", requestId)
                        .with(authenticatedAs(EMPLOYEE_ID, "EMPLOYEE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody(vacationTypeId, "2026-11-09", "2026-11-13", "Five days")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workingDays").value(5));

        assertThat(jdbcTemplate.queryForObject(
                "select reserved_days from leave_balances where user_id = ? and leave_type_id = ?",
                Integer.class, EMPLOYEE_ID, vacationTypeId)).isEqualTo(5);
        assertThat(jdbcTemplate.queryForList(
                "select kind || ':' || change_days from balance_ledger where request_id = ? order by id",
                String.class, requestId)).containsExactly("RESERVE:4", "RELEASE:-4", "RESERVE:5");
    }

    @Test
    void editOfTerminalRequestReturnsConflictWithoutChangingRequestOrBalance() throws Exception {
        long vacationTypeId = leaveTypeId("Vacation");
        for (String terminalStatus : new String[] {"APPROVED", "REJECTED", "CANCELLED"}) {
            long requestId = insertRequest(EMPLOYEE_ID, MANAGER_ID, vacationTypeId, terminalStatus);

            mockMvc.perform(put("/requests/{requestId}", requestId)
                            .with(authenticatedAs(EMPLOYEE_ID, "EMPLOYEE"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(updateBody(vacationTypeId, "2026-10-30", "2026-10-30", "Changed")))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("INVALID_TRANSITION"));

            assertThat(jdbcTemplate.queryForObject(
                    "select status from leave_requests where id = ?", String.class, requestId))
                    .isEqualTo(terminalStatus);
            assertThat(jdbcTemplate.queryForObject(
                    "select count(*) from audit_events where entity_id = ? and action = 'LEAVE_REQUEST_EDITED'",
                    Integer.class, requestId)).isZero();
        }
        assertThat(jdbcTemplate.queryForObject(
                "select reserved_days from leave_balances where user_id = ? and leave_type_id = ?",
                Integer.class, EMPLOYEE_ID, vacationTypeId)).isZero();
    }

    @Test
    void changingLeaveTypeIsRejectedWithoutChangingRequestOrReservation() throws Exception {
        long vacationTypeId = leaveTypeId("Vacation");
        long sickTypeId = leaveTypeId("Sick");
        long requestId = createPendingRequest(vacationTypeId, "2026-10-29", "2026-11-03", "Original");

        mockMvc.perform(put("/requests/{requestId}", requestId)
                        .with(authenticatedAs(EMPLOYEE_ID, "EMPLOYEE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody(sickTypeId, "2026-10-30", "2026-11-03", "Changed")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("LEAVE_TYPE_CHANGE_NOT_ALLOWED"));

        assertThat(jdbcTemplate.queryForObject(
                "select leave_type_id from leave_requests where id = ?", Long.class, requestId)).isEqualTo(vacationTypeId);
        assertThat(jdbcTemplate.queryForObject(
                "select working_days from leave_requests where id = ?", Integer.class, requestId)).isEqualTo(4);
        assertThat(jdbcTemplate.queryForObject(
                "select reserved_days from leave_balances where user_id = ? and leave_type_id = ?",
                Integer.class, EMPLOYEE_ID, vacationTypeId)).isEqualTo(4);
    }

    @Test
    void invalidEditDateRangesReturnValidationErrorWithoutChangingRequest() throws Exception {
        long vacationTypeId = leaveTypeId("Vacation");
        long requestId = createPendingRequest(vacationTypeId, "2026-10-29", "2026-11-03", "Original");
        for (String[] dates : new String[][] {
                {"2026-11-03", "2026-10-29"},
                {"2026-10-31", "2026-11-01"}
        }) {
            mockMvc.perform(put("/requests/{requestId}", requestId)
                            .with(authenticatedAs(EMPLOYEE_ID, "EMPLOYEE"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(updateBody(vacationTypeId, dates[0], dates[1], "Changed")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        }

        assertThat(jdbcTemplate.queryForObject(
                "select start_date::text || ':' || end_date::text || ':' || working_days || ':' || notes "
                        + "from leave_requests where id = ?",
                String.class, requestId)).isEqualTo("2026-10-29:2026-11-03:4:Original");
        assertThat(jdbcTemplate.queryForObject(
                "select reserved_days from leave_balances where user_id = ? and leave_type_id = ?",
                Integer.class, EMPLOYEE_ID, vacationTypeId)).isEqualTo(4);
    }

    @Test
    void missingRequiredEditFieldReturnsValidationErrorWithoutChangingRequest() throws Exception {
        long vacationTypeId = leaveTypeId("Vacation");
        long requestId = createPendingRequest(vacationTypeId, "2026-10-29", "2026-11-03", "Original");

        mockMvc.perform(put("/requests/{requestId}", requestId)
                        .with(authenticatedAs(EMPLOYEE_ID, "EMPLOYEE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "leaveTypeId": %d,
                                    "endDate": "2026-11-03",
                                    "notes": "Missing start date"
                                }
                                """.formatted(vacationTypeId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        assertThat(jdbcTemplate.queryForObject(
                "select start_date::text || ':' || end_date::text || ':' || working_days || ':' || notes "
                        + "from leave_requests where id = ?",
                String.class, requestId)).isEqualTo("2026-10-29:2026-11-03:4:Original");
        assertThat(jdbcTemplate.queryForObject(
                "select reserved_days from leave_balances where user_id = ? and leave_type_id = ?",
                Integer.class, EMPLOYEE_ID, vacationTypeId)).isEqualTo(4);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from balance_ledger where request_id = ?", Integer.class, requestId)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from audit_events where entity_id = ? and action = 'LEAVE_REQUEST_EDITED'",
                Integer.class, requestId)).isZero();
    }

    @Test
    void unrepresentableEditRangeReturnsValidationErrorWithoutChangingReservation() throws Exception {
        long vacationTypeId = leaveTypeId("Vacation");
        long requestId = createPendingRequest(vacationTypeId, "2026-10-29", "2026-11-03", "Original");

        mockMvc.perform(put("/requests/{requestId}", requestId)
                        .with(authenticatedAs(EMPLOYEE_ID, "EMPLOYEE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody(vacationTypeId, "0001-01-01", "+999999999-12-31", "Too broad")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        assertThat(jdbcTemplate.queryForObject(
                "select working_days from leave_requests where id = ?", Integer.class, requestId)).isEqualTo(4);
        assertThat(jdbcTemplate.queryForObject(
                "select reserved_days from leave_balances where user_id = ? and leave_type_id = ?",
                Integer.class, EMPLOYEE_ID, vacationTypeId)).isEqualTo(4);
    }

    @Test
    void insufficientAdditionalBalanceLeavesRequestReservationAndLedgerUnchanged() throws Exception {
        long vacationTypeId = leaveTypeId("Vacation");
        long requestId = createPendingRequest(vacationTypeId, "2026-10-29", "2026-11-03", "Original");
        jdbcTemplate.update(
                "update leave_balances set total_days = 4 where user_id = ? and leave_type_id = ?",
                EMPLOYEE_ID, vacationTypeId);

        mockMvc.perform(put("/requests/{requestId}", requestId)
                        .with(authenticatedAs(EMPLOYEE_ID, "EMPLOYEE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody(vacationTypeId, "2026-11-09", "2026-11-13", "Five days")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_BALANCE"));

        assertThat(jdbcTemplate.queryForObject(
                "select start_date::text || ':' || end_date::text || ':' || working_days || ':' || notes "
                        + "from leave_requests where id = ?",
                String.class, requestId)).isEqualTo("2026-10-29:2026-11-03:4:Original");
        assertThat(jdbcTemplate.queryForObject(
                "select reserved_days from leave_balances where user_id = ? and leave_type_id = ?",
                Integer.class, EMPLOYEE_ID, vacationTypeId)).isEqualTo(4);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from balance_ledger where request_id = ?", Integer.class, requestId)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from audit_events where entity_id = ? and action = 'LEAVE_REQUEST_EDITED'",
                Integer.class, requestId)).isZero();
    }

    @Test
    void onlyOwnerCanEditAndDeniedAccessIsLogged() throws Exception {
        long vacationTypeId = leaveTypeId("Vacation");
        long requestId = createPendingRequest(vacationTypeId, "2026-10-29", "2026-11-03", "Original");
        long[] actors = {6L, MANAGER_ID, 1L};
        String[] roles = {"EMPLOYEE", "MANAGER", "HR_ADMIN"};

        for (int index = 0; index < actors.length; index++) {
            mockMvc.perform(put("/requests/{requestId}", requestId)
                            .with(forwardedApiPrefix())
                            .with(authenticatedAs(actors[index], roles[index]))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(updateBody(vacationTypeId, "2026-10-30", "2026-11-03", "Unauthorized")))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
            assertThat(jdbcTemplate.queryForObject(
                    "select count(*) from access_log where actor_id = ? and resource = ? and allowed = false",
                    Integer.class, actors[index], "PUT /api/requests/" + requestId)).isEqualTo(1);
        }

        assertThat(jdbcTemplate.queryForObject(
                "select notes from leave_requests where id = ?", String.class, requestId)).isEqualTo("Original");
        assertThat(jdbcTemplate.queryForObject(
                "select reserved_days from leave_balances where user_id = ? and leave_type_id = ?",
                Integer.class, EMPLOYEE_ID, vacationTypeId)).isEqualTo(4);
    }

    @Test
    void auditFailureRollsBackEditRequestReservationAndLedger() throws Exception {
        long vacationTypeId = leaveTypeId("Vacation");
        long requestId = createPendingRequest(vacationTypeId, "2026-10-29", "2026-11-03", "Original");
        jdbcTemplate.execute("""
                create or replace function reject_test_edit_audit() returns trigger as $$
                begin
                    if new.action = 'LEAVE_REQUEST_EDITED' then
                        raise exception 'intentional edit audit failure';
                    end if;
                    return new;
                end;
                $$ language plpgsql
                """);
        jdbcTemplate.execute("""
                create trigger reject_test_edit_audit
                before insert on audit_events
                for each row execute function reject_test_edit_audit()
                """);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> mockMvc.perform(
                        put("/requests/{requestId}", requestId)
                                .with(authenticatedAs(EMPLOYEE_ID, "EMPLOYEE"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(updateBody(vacationTypeId, "2026-10-30", "2026-11-03", "Changed"))))
                .hasMessageContaining("intentional edit audit failure");

        assertThat(jdbcTemplate.queryForObject(
                "select start_date::text || ':' || end_date::text || ':' || working_days || ':' || notes "
                        + "from leave_requests where id = ?",
                String.class, requestId)).isEqualTo("2026-10-29:2026-11-03:4:Original");
        assertThat(jdbcTemplate.queryForObject(
                "select reserved_days from leave_balances where user_id = ? and leave_type_id = ?",
                Integer.class, EMPLOYEE_ID, vacationTypeId)).isEqualTo(4);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from balance_ledger where request_id = ?", Integer.class, requestId)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from audit_events where entity_id = ? and action = 'LEAVE_REQUEST_EDITED'",
                Integer.class, requestId)).isZero();
    }

    @Test
    void cancelPendingRequestReleasesDaysAuditsAndRemainsVisible() throws Exception {
        long vacationTypeId = leaveTypeId("Vacation");
        long requestId = createPendingRequest(vacationTypeId, "2026-10-29", "2026-11-03", "Original");

        mockMvc.perform(post("/requests/{requestId}/cancel", requestId)
                        .with(authenticatedAs(EMPLOYEE_ID, "EMPLOYEE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value((int) requestId))
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.decidedAt").isEmpty());

        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from leave_requests where id = ?", Integer.class, requestId)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "select status from leave_requests where id = ?", String.class, requestId)).isEqualTo("CANCELLED");
        assertThat(jdbcTemplate.queryForObject(
                "select reserved_days from leave_balances where user_id = ? and leave_type_id = ?",
                Integer.class, EMPLOYEE_ID, vacationTypeId)).isZero();

        MvcResult listedRequests = mockMvc.perform(get("/requests")
                        .with(authenticatedAs(EMPLOYEE_ID, "EMPLOYEE")))
                .andExpect(status().isOk())
                .andReturn();
        List<String> visibleStatuses = JsonPath.read(
                listedRequests.getResponse().getContentAsString(),
                "$[?(@.id == " + requestId + ")].status");
        assertThat(visibleStatuses).containsExactly("CANCELLED");

        assertThat(jdbcTemplate.queryForObject("""
                select count(*) from balance_ledger
                where request_id = ? and kind = 'RELEASE' and change_days = -4
                  and actor_id = ? and created_at is not null
                """, Integer.class, requestId, EMPLOYEE_ID)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from balance_ledger where request_id = ? and kind = 'RELEASE'",
                Integer.class, requestId)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("""
                select count(*) from audit_events
                where entity = 'LEAVE_REQUEST' and entity_id = ?
                  and action = 'LEAVE_REQUEST_CANCELLED' and actor_id = ? and created_at is not null
                """, Integer.class, requestId, EMPLOYEE_ID)).isEqualTo(1);

        long replacementRequestId =
                createPendingRequest(vacationTypeId, "2026-10-29", "2026-11-03", "Replacement");
        assertThat(replacementRequestId).isNotEqualTo(requestId);
        assertThat(jdbcTemplate.queryForObject(
                "select reserved_days from leave_balances where user_id = ? and leave_type_id = ?",
                Integer.class, EMPLOYEE_ID, vacationTypeId)).isEqualTo(4);
    }

    @Test
    void cancellationRejectsEveryTerminalStatusWithoutChangingBalanceOrHistory() throws Exception {
        long vacationTypeId = leaveTypeId("Vacation");
        long requestId = createPendingRequest(vacationTypeId, "2026-10-29", "2026-11-03", "Original");

        for (String terminalStatus : List.of("APPROVED", "REJECTED", "CANCELLED")) {
            jdbcTemplate.update("update leave_requests set status = ? where id = ?", terminalStatus, requestId);

            mockMvc.perform(post("/requests/{requestId}/cancel", requestId)
                            .with(authenticatedAs(EMPLOYEE_ID, "EMPLOYEE")))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("INVALID_TRANSITION"));

            assertThat(jdbcTemplate.queryForObject(
                    "select status from leave_requests where id = ?", String.class, requestId))
                    .isEqualTo(terminalStatus);
            assertThat(jdbcTemplate.queryForObject(
                    "select reserved_days from leave_balances where user_id = ? and leave_type_id = ?",
                    Integer.class, EMPLOYEE_ID, vacationTypeId)).isEqualTo(4);
            assertThat(jdbcTemplate.queryForObject(
                    "select count(*) from balance_ledger where request_id = ? and kind = 'RELEASE'",
                    Integer.class, requestId)).isZero();
            assertThat(jdbcTemplate.queryForObject(
                    "select count(*) from audit_events where entity_id = ? and action = 'LEAVE_REQUEST_CANCELLED'",
                    Integer.class, requestId)).isZero();
        }
    }

    @Test
    void onlyOwnerCanCancelAndDeniedAccessIsLogged() throws Exception {
        long vacationTypeId = leaveTypeId("Vacation");
        long requestId = createPendingRequest(vacationTypeId, "2026-10-29", "2026-11-03", "Original");
        long[] actors = {6L, MANAGER_ID, 1L};
        String[] roles = {"EMPLOYEE", "MANAGER", "HR_ADMIN"};

        for (int index = 0; index < actors.length; index++) {
            mockMvc.perform(post("/requests/{requestId}/cancel", requestId)
                            .with(forwardedApiPrefix())
                            .with(authenticatedAs(actors[index], roles[index])))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
            assertThat(jdbcTemplate.queryForObject(
                    "select count(*) from access_log where actor_id = ? and resource = ? and allowed = false",
                    Integer.class, actors[index], "POST /api/requests/" + requestId + "/cancel")).isEqualTo(1);
        }

        assertThat(jdbcTemplate.queryForObject(
                "select status from leave_requests where id = ?", String.class, requestId)).isEqualTo("PENDING");
        assertThat(jdbcTemplate.queryForObject(
                "select reserved_days from leave_balances where user_id = ? and leave_type_id = ?",
                Integer.class, EMPLOYEE_ID, vacationTypeId)).isEqualTo(4);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from balance_ledger where request_id = ? and kind = 'RELEASE'",
                Integer.class, requestId)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from audit_events where entity_id = ? and action = 'LEAVE_REQUEST_CANCELLED'",
                Integer.class, requestId)).isZero();
    }

    @Test
    void cancellingUnknownRequestReturnsNotFound() throws Exception {
        mockMvc.perform(post("/requests/{requestId}/cancel", 99999999L)
                        .with(authenticatedAs(EMPLOYEE_ID, "EMPLOYEE")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void cancellingWithoutBalanceReturnsInsufficientBalanceWithoutMutation() throws Exception {
        long vacationTypeId = leaveTypeId("Vacation");
        long requestId = createPendingRequest(vacationTypeId, "2026-10-29", "2026-11-03", "Original");
        jdbcTemplate.update(
                "delete from leave_balances where user_id = ? and leave_type_id = ?",
                EMPLOYEE_ID, vacationTypeId);

        mockMvc.perform(post("/requests/{requestId}/cancel", requestId)
                        .with(authenticatedAs(EMPLOYEE_ID, "EMPLOYEE")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_BALANCE"));

        assertThat(jdbcTemplate.queryForObject(
                "select status from leave_requests where id = ?", String.class, requestId)).isEqualTo("PENDING");
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from leave_balances where user_id = ? and leave_type_id = ?",
                Integer.class, EMPLOYEE_ID, vacationTypeId)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from balance_ledger where request_id = ? and kind = 'RELEASE'",
                Integer.class, requestId)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from audit_events where entity_id = ? and action = 'LEAVE_REQUEST_CANCELLED'",
                Integer.class, requestId)).isZero();
    }

    @Test
    void cancellingWithInsufficientReservedDaysReturnsConflictWithoutMutation() throws Exception {
        long vacationTypeId = leaveTypeId("Vacation");
        long requestId = createPendingRequest(vacationTypeId, "2026-10-29", "2026-11-03", "Original");
        jdbcTemplate.update(
                "update leave_balances set reserved_days = 2 where user_id = ? and leave_type_id = ?",
                EMPLOYEE_ID, vacationTypeId);

        mockMvc.perform(post("/requests/{requestId}/cancel", requestId)
                        .with(authenticatedAs(EMPLOYEE_ID, "EMPLOYEE")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_BALANCE"));

        assertThat(jdbcTemplate.queryForObject(
                "select status from leave_requests where id = ?", String.class, requestId)).isEqualTo("PENDING");
        assertThat(jdbcTemplate.queryForObject(
                "select reserved_days from leave_balances where user_id = ? and leave_type_id = ?",
                Integer.class, EMPLOYEE_ID, vacationTypeId)).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from balance_ledger where request_id = ? and kind = 'RELEASE'",
                Integer.class, requestId)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from audit_events where entity_id = ? and action = 'LEAVE_REQUEST_CANCELLED'",
                Integer.class, requestId)).isZero();
    }

    @Test
    void cancellationAuditFailureRollsBackStatusReservationAndLedger() throws Exception {
        long vacationTypeId = leaveTypeId("Vacation");
        long requestId = createPendingRequest(vacationTypeId, "2026-10-29", "2026-11-03", "Original");
        jdbcTemplate.execute("""
                create or replace function reject_test_cancel_audit() returns trigger as $$
                begin
                    if new.action = 'LEAVE_REQUEST_CANCELLED' then
                        raise exception 'intentional cancel audit failure';
                    end if;
                    return new;
                end;
                $$ language plpgsql
                """);
        jdbcTemplate.execute("""
                create trigger reject_test_cancel_audit
                before insert on audit_events
                for each row execute function reject_test_cancel_audit()
                """);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> mockMvc.perform(
                        post("/requests/{requestId}/cancel", requestId)
                                .with(authenticatedAs(EMPLOYEE_ID, "EMPLOYEE"))))
                .hasMessageContaining("intentional cancel audit failure");

        assertThat(jdbcTemplate.queryForObject(
                "select status from leave_requests where id = ?", String.class, requestId)).isEqualTo("PENDING");
        assertThat(jdbcTemplate.queryForObject(
                "select reserved_days from leave_balances where user_id = ? and leave_type_id = ?",
                Integer.class, EMPLOYEE_ID, vacationTypeId)).isEqualTo(4);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from balance_ledger where request_id = ? and kind = 'RELEASE'",
                Integer.class, requestId)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from audit_events where entity_id = ? and action = 'LEAVE_REQUEST_CANCELLED'",
                Integer.class, requestId)).isZero();
    }

    private long leaveTypeId(String name) {
        return jdbcTemplate.queryForObject("select id from leave_types where name = ?", Long.class, name);
    }

    private long createPendingRequest(long leaveTypeId, String startDate, String endDate, String notes)
            throws Exception {
        MvcResult result = mockMvc.perform(post("/requests")
                        .with(authenticatedAs(EMPLOYEE_ID, "EMPLOYEE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "leaveTypeId": %d,
                                    "startDate": "%s",
                                    "endDate": "%s",
                                    "notes": "%s"
                                }
                                """.formatted(leaveTypeId, startDate, endDate, notes)))
                .andExpect(status().isCreated())
                .andReturn();
        return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.id")).longValue();
    }

    private String updateBody(long leaveTypeId, String startDate, String endDate, String notes) {
        return """
                {
                    "leaveTypeId": %d,
                    "startDate": "%s",
                    "endDate": "%s",
                    "notes": "%s"
                }
                """.formatted(leaveTypeId, startDate, endDate, notes);
    }

    private int performEdit(long requestId, long leaveTypeId, String startDate, String endDate, String notes)
            throws Exception {
        return mockMvc.perform(put("/requests/{requestId}", requestId)
                        .with(authenticatedAs(EMPLOYEE_ID, "EMPLOYEE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody(leaveTypeId, startDate, endDate, notes)))
                .andReturn()
                .getResponse()
                .getStatus();
    }

    private void awaitRequestLockWaiters() throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            int waiting = jdbcTemplate.queryForObject("""
                    select count(*)
                    from pg_stat_activity
                    where datname = current_database()
                      and wait_event_type = 'Lock'
                      and (query ilike '%leave_balances%' or query ilike '%leave_requests%')
                    """, Integer.class);
            if (waiting >= 2) {
                return;
            }
            Thread.sleep(25);
        }
        throw new AssertionError("Both edit transactions did not reach a row-lock wait");
    }

    private long insertRequest(long employeeId, long approverId, long leaveTypeId, String status) {
        return jdbcTemplate.queryForObject("""
                insert into leave_requests (
                    employee_id, approver_id, leave_type_id, start_date, end_date,
                    working_days, status, submitted_at
                ) values (?, ?, ?, '2026-10-29', '2026-10-29', 1, ?, now())
                returning id
                """, Long.class, employeeId, approverId, leaveTypeId, status);
    }

    private void dropNotificationFailureTrigger() {
        jdbcTemplate.execute("drop trigger if exists reject_test_notification on notifications");
        jdbcTemplate.execute("drop function if exists reject_test_notification()");
    }

    private void dropEditAuditFailureTrigger() {
        jdbcTemplate.execute("drop trigger if exists reject_test_edit_audit on audit_events");
        jdbcTemplate.execute("drop function if exists reject_test_edit_audit()");
    }

    private void dropCancelAuditFailureTrigger() {
        jdbcTemplate.execute("drop trigger if exists reject_test_cancel_audit on audit_events");
        jdbcTemplate.execute("drop function if exists reject_test_cancel_audit()");
    }

    private RequestPostProcessor forwardedApiPrefix() {
        return request -> {
            request.addHeader("X-Forwarded-Prefix", "/api");
            return request;
        };
    }

    private RequestPostProcessor authenticatedAs(long userId, String role) {
        return authentication(new TestingAuthenticationToken(
                userId, null, new SimpleGrantedAuthority("ROLE_" + role)));
    }
}
