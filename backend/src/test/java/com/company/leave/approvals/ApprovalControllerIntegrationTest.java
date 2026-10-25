package com.company.leave.approvals;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import javax.sql.DataSource;

import com.jayway.jsonpath.JsonPath;
import com.company.leave.leave.LeaveRequestService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.test.context.SpringBootTest;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class ApprovalControllerIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18");

    private static final long HR_ID = 1L;
    private static final long TOP_LEVEL_MANAGER_ID = 2L;
    private static final long MANAGER_ID = 3L;
    private static final long OTHER_MANAGER_ID = 4L;
    private static final long EMPLOYEE_ID = 5L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private LeaveRequestService leaveRequestService;

    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.update("delete from active_sessions");
        jdbcTemplate.update("delete from notifications");
        jdbcTemplate.update("delete from balance_ledger");
        jdbcTemplate.update("delete from leave_requests");
        jdbcTemplate.update("""
                update leave_balances balance
                   set total_days = case leave_type.name
                       when 'Vacation' then 20
                       when 'Sick' then 10
                       when 'Parental' then 30
                       else 0
                   end,
                       reserved_days = 0
                  from leave_types leave_type
                 where leave_type.id = balance.leave_type_id
                """);
        jdbcTemplate.update(
                "update users set must_change_password = false where id in (?, ?, ?, ?, ?)",
                HR_ID, TOP_LEVEL_MANAGER_ID, MANAGER_ID, OTHER_MANAGER_ID, EMPLOYEE_ID);
    }

    @Test
    void managerQueueContainsOnlyAssignedPendingRequestsAndIncludesDecisionContext() throws Exception {
        long vacationId = leaveTypeId("Vacation");
        long firstRequest = insertRequest(EMPLOYEE_ID, MANAGER_ID, vacationId, "2026-10-29", "2026-11-03");
        long otherTeamRequest = insertRequest(9L, OTHER_MANAGER_ID, vacationId, "2026-11-05", "2026-11-06");
        long managerOwnRequest = insertRequest(MANAGER_ID, TOP_LEVEL_MANAGER_ID, vacationId, "2026-11-12", "2026-11-13");
        long approvedRequest = insertRequest(6L, MANAGER_ID, vacationId, "2026-11-09", "2026-11-10");
        jdbcTemplate.update("update leave_requests set status = 'APPROVED' where id = ?", approvedRequest);
        jdbcTemplate.update("""
                update leave_balances
                   set reserved_days = 4
                 where user_id = ? and leave_type_id = ?
                """, EMPLOYEE_ID, vacationId);

        insertHistory(firstRequest, "LEAVE_REQUEST_SUBMITTED", EMPLOYEE_ID,
                "{\"detail\":\"Submitted Vacation, 2026-10-29 to 2026-11-03 (4 working days).\"}",
                Instant.parse("2026-10-20T09:00:00Z"));
        insertHistory(firstRequest, "LEAVE_REQUEST_EDITED", EMPLOYEE_ID,
                "{\"detail\":\"Updated the dates.\"}", Instant.parse("2026-10-21T09:00:00Z"));
        insertHistoryForEntity(firstRequest + 1000, "REQUEST_COMMENT_ADDED", MANAGER_ID,
                "{\"requestId\":" + firstRequest + ",\"comment\":\"Please confirm coverage.\"}",
                Instant.parse("2026-10-22T09:00:00Z"));
        insertHistoryForEntity(firstRequest + 1001, "REQUEST_COMMENT_ADDED", EMPLOYEE_ID,
                "{\"requestId\":" + firstRequest + ",\"comment\":\"Coverage is arranged.\"}",
                Instant.parse("2026-10-23T09:00:00Z"));
        insertHistoryForEntity(otherTeamRequest + 1000, "REQUEST_COMMENT_ADDED", MANAGER_ID,
                "{\"requestId\":" + otherTeamRequest + ",\"comment\":\"Other request must not leak.\"}",
                Instant.parse("2026-10-22T12:00:00Z"));

        MvcResult result = mockMvc.perform(get("/approvals/pending")
                        .with(authenticatedAs(MANAGER_ID, "MANAGER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(firstRequest))
                .andExpect(jsonPath("$[0].employeeId").value(EMPLOYEE_ID))
                .andExpect(jsonPath("$[0].approverId").value(MANAGER_ID))
                .andExpect(jsonPath("$[0].leaveTypeId").value(vacationId))
                .andExpect(jsonPath("$[0].startDate").value("2026-10-29"))
                .andExpect(jsonPath("$[0].endDate").value("2026-11-03"))
                .andExpect(jsonPath("$[0].workingDays").value(4))
                .andExpect(jsonPath("$[0].notes").value("Dashboard test"))
                .andExpect(jsonPath("$[0].status").value("PENDING"))
                .andExpect(jsonPath("$[0].submittedAt").exists())
                .andExpect(jsonPath("$[0].decidedAt").value((Object) null))
                .andExpect(jsonPath("$[0].decisionComment").value((Object) null))
                .andExpect(jsonPath("$[0].employeeUsername").value("emp.jsmith"))
                .andExpect(jsonPath("$[0].leaveTypeName").value("Vacation"))
                .andExpect(jsonPath("$[0].balance.userId").value(EMPLOYEE_ID))
                .andExpect(jsonPath("$[0].balance.leaveTypeId").value(vacationId))
                .andExpect(jsonPath("$[0].balance.totalDays").value(20))
                .andExpect(jsonPath("$[0].balance.reservedDays").value(4))
                .andExpect(jsonPath("$[0].balance.availableDays").value(16))
                .andExpect(jsonPath("$[0].comments.length()").value(2))
                .andExpect(jsonPath("$[0].comments[0].requestId").value(firstRequest))
                .andExpect(jsonPath("$[0].comments[0].authorId").value(MANAGER_ID))
                .andExpect(jsonPath("$[0].comments[0].authorUsername").value("mgr.chen"))
                .andExpect(jsonPath("$[0].comments[0].body").value("Please confirm coverage."))
                .andExpect(jsonPath("$[0].comments[0].createdAt").value("2026-10-22T09:00:00Z"))
                .andExpect(jsonPath("$[0].comments[1].requestId").value(firstRequest))
                .andExpect(jsonPath("$[0].comments[1].authorId").value(EMPLOYEE_ID))
                .andExpect(jsonPath("$[0].comments[1].authorUsername").value("emp.jsmith"))
                .andExpect(jsonPath("$[0].comments[1].body").value("Coverage is arranged."))
                .andExpect(jsonPath("$[0].comments[1].createdAt").value("2026-10-23T09:00:00Z"))
                .andExpect(jsonPath("$[0].history.length()").value(4))
                .andExpect(jsonPath("$[0].history[0].type").value("SUBMITTED"))
                .andExpect(jsonPath("$[0].history[0].actorId").value(EMPLOYEE_ID))
                .andExpect(jsonPath("$[0].history[0].detail")
                        .value("Submitted Vacation, 2026-10-29 to 2026-11-03 (4 working days)."))
                .andExpect(jsonPath("$[0].history[1].type").value("EDITED"))
                .andExpect(jsonPath("$[0].history[1].actorId").value(EMPLOYEE_ID))
                .andExpect(jsonPath("$[0].history[1].detail").value("Updated the dates."))
                .andExpect(jsonPath("$[0].history[2].type").value("COMMENT"))
                .andExpect(jsonPath("$[0].history[2].actorId").value(MANAGER_ID))
                .andExpect(jsonPath("$[0].history[2].detail").value("Please confirm coverage."))
                .andExpect(jsonPath("$[0].history[3].type").value("COMMENT"))
                .andExpect(jsonPath("$[0].history[3].actorId").value(EMPLOYEE_ID))
                .andExpect(jsonPath("$[0].history[3].detail").value("Coverage is arranged."))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(List.of(
                        JsonPath.<String>read(body, "$[0].history[0].occurredAt"),
                        JsonPath.<String>read(body, "$[0].history[1].occurredAt"),
                        JsonPath.<String>read(body, "$[0].history[2].occurredAt"),
                        JsonPath.<String>read(body, "$[0].history[3].occurredAt")))
                .containsExactly(
                        "2026-10-20T09:00:00Z",
                        "2026-10-21T09:00:00Z",
                        "2026-10-22T09:00:00Z",
                        "2026-10-23T09:00:00Z");
        assertThat(JsonPath.<Number>read(body, "$[0].comments[0].id").longValue()).isPositive();
        assertThat(JsonPath.<Number>read(body, "$[0].comments[1].id").longValue()).isPositive();
        assertThat(otherTeamRequest).isNotEqualTo(firstRequest);
        assertThat(managerOwnRequest).isNotEqualTo(firstRequest);
    }

    @Test
    void legacyCommentWithoutBodyUsesHistoryFallback() throws Exception {
        long requestId = insertRequest(EMPLOYEE_ID, MANAGER_ID, leaveTypeId("Vacation"),
                "2026-10-29", "2026-11-03");
        insertHistoryForEntity(requestId + 1000, "REQUEST_COMMENT_ADDED", MANAGER_ID,
                "{\"requestId\":" + requestId + "}", Instant.parse("2026-10-22T09:00:00Z"));

        mockMvc.perform(get("/approvals/pending").with(authenticatedAs(MANAGER_ID, "MANAGER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].comments.length()").value(1))
                .andExpect(jsonPath("$[0].comments[0].body").value("Event details were not recorded."));
    }

    @Test
    void hrQueueIncludesOwnAndTopLevelManagerDecisionsOnly() throws Exception {
        long vacationId = leaveTypeId("Vacation");
        long topManagerRequest = insertRequest(TOP_LEVEL_MANAGER_ID, HR_ID, vacationId, "2026-11-02", "2026-11-03");
        long hrSelfRequest = insertRequest(HR_ID, HR_ID, vacationId, "2026-11-04", "2026-11-05");
        long approvedRequest = insertRequest(EMPLOYEE_ID, HR_ID, vacationId, "2026-11-06", "2026-11-09");
        jdbcTemplate.update("update leave_requests set status = 'APPROVED' where id = ?", approvedRequest);
        insertRequest(EMPLOYEE_ID, MANAGER_ID, vacationId, "2026-11-09", "2026-11-10");

        mockMvc.perform(get("/approvals/pending").with(authenticatedAs(HR_ID, "HR_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].employeeId").value(TOP_LEVEL_MANAGER_ID))
                .andExpect(jsonPath("$[0].approverId").value(HR_ID))
                .andExpect(jsonPath("$[1].employeeId").value(HR_ID))
                .andExpect(jsonPath("$[1].approverId").value(HR_ID));

        assertThat(topManagerRequest).isNotEqualTo(hrSelfRequest);
    }

    @Test
    void emptyQueueReturnsAnEmptyArray() throws Exception {
        mockMvc.perform(get("/approvals/pending").with(authenticatedAs(OTHER_MANAGER_ID, "MANAGER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void employeeIsDeniedAndDenialIsLogged() throws Exception {
        mockMvc.perform(get("/approvals/pending")
                        .header("X-Forwarded-Prefix", "/api")
                        .with(authenticatedAs(EMPLOYEE_ID, "EMPLOYEE")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        assertThat(jdbcTemplate.queryForObject("""
                select count(*) from access_log
                 where actor_id = ? and resource = 'GET /api/approvals/pending' and allowed = false
                """, Long.class, EMPLOYEE_ID)).isPositive();
    }

    @Test
    void unauthenticatedCallerIsRejected() throws Exception {
        mockMvc.perform(get("/approvals/pending"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void assignedManagerCanApproveWithCommentAndAllEffectsAreCommitted() throws Exception {
        long vacationId = leaveTypeId("Vacation");
        long requestId = insertRequest(EMPLOYEE_ID, MANAGER_ID, vacationId, "2026-10-29", "2026-11-03");
        reserve(EMPLOYEE_ID, vacationId, 4);

        mockMvc.perform(post("/approvals/{requestId}/approve", requestId)
                        .with(authenticatedAs(MANAGER_ID, "MANAGER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"Enjoy your trip!\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(requestId))
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.approverId").value(MANAGER_ID))
                .andExpect(jsonPath("$.decidedAt").isNotEmpty())
                .andExpect(jsonPath("$.decisionComment").value("Enjoy your trip!"));

        assertThat(jdbcTemplate.queryForObject(
                "select status from leave_requests where id = ?", String.class, requestId)).isEqualTo("APPROVED");
        assertThat(jdbcTemplate.queryForObject(
                "select approver_id from leave_requests where id = ?", Long.class, requestId))
                .isEqualTo(MANAGER_ID);
        assertThat(jdbcTemplate.queryForObject(
                "select decided_at from leave_requests where id = ?", Timestamp.class, requestId)).isNotNull();
        assertThat(jdbcTemplate.queryForObject(
                "select decision_comment from leave_requests where id = ?", String.class, requestId))
                .isEqualTo("Enjoy your trip!");
        assertBalance(EMPLOYEE_ID, vacationId, 16, 0);
        assertThat(jdbcTemplate.queryForObject("""
                select total_days - reserved_days from leave_balances
                 where user_id = ? and leave_type_id = ?
                """, Integer.class, EMPLOYEE_ID, vacationId)).isEqualTo(16);
        assertThat(jdbcTemplate.queryForObject("""
                select count(*) from balance_ledger
                 where request_id = ? and kind = 'DEDUCT' and change_days = -4
                   and actor_id = ? and created_at is not null
                """, Long.class, requestId, MANAGER_ID)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("""
                select count(*) from audit_events
                 where entity = 'LEAVE_REQUEST' and entity_id = ?
                   and action = 'LEAVE_REQUEST_APPROVED' and actor_id = ?
                """, Long.class, requestId, MANAGER_ID)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("""
                select count(*) from notifications
                 where user_id = ? and request_id = ? and kind = 'APPROVED'
                """, Long.class, EMPLOYEE_ID, requestId)).isEqualTo(1);
    }

    @Test
    void assignedManagerCanRejectWithReasonAndAllEffectsAreCommitted() throws Exception {
        long vacationId = leaveTypeId("Vacation");
        long requestId = insertRequest(EMPLOYEE_ID, MANAGER_ID, vacationId, "2026-10-29", "2026-11-03");
        reserve(EMPLOYEE_ID, vacationId, 4);
        String reason = "Please resubmit after the month-end close.";

        mockMvc.perform(post("/approvals/{requestId}/reject", requestId)
                        .with(authenticatedAs(MANAGER_ID, "MANAGER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"" + reason + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(requestId))
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.approverId").value(MANAGER_ID))
                .andExpect(jsonPath("$.decidedAt").isNotEmpty())
                .andExpect(jsonPath("$.decisionComment").value(reason));

        assertThat(jdbcTemplate.queryForObject(
                "select status from leave_requests where id = ?", String.class, requestId)).isEqualTo("REJECTED");
        assertThat(jdbcTemplate.queryForObject(
                "select decision_comment from leave_requests where id = ?", String.class, requestId))
                .isEqualTo(reason);
        assertBalance(EMPLOYEE_ID, vacationId, 20, 0);
        assertThat(jdbcTemplate.queryForObject("""
                select total_days - reserved_days from leave_balances
                 where user_id = ? and leave_type_id = ?
                """, Integer.class, EMPLOYEE_ID, vacationId)).isEqualTo(20);
        assertThat(jdbcTemplate.queryForObject("""
                select count(*) from balance_ledger
                 where request_id = ? and kind = 'RELEASE' and change_days = -4
                   and actor_id = ? and reason = ?
                """, Long.class, requestId, MANAGER_ID, "Rejected leave request " + requestId)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("""
                select count(*) from audit_events
                 where entity = 'LEAVE_REQUEST' and entity_id = ?
                   and action = 'LEAVE_REQUEST_REJECTED' and actor_id = ?
                   and details_json ->> 'detail' = ?
                """, Long.class, requestId, MANAGER_ID, reason)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("""
                select count(*) from notifications
                 where user_id = ? and request_id = ? and kind = 'REJECTED'
                """, Long.class, EMPLOYEE_ID, requestId)).isEqualTo(1);

        mockMvc.perform(get("/requests/{requestId}/history", requestId)
                        .with(authenticatedAs(MANAGER_ID, "MANAGER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].type").value("REJECTED"))
                .andExpect(jsonPath("$[0].detail").value(reason));
        mockMvc.perform(get("/requests/{requestId}", requestId)
                        .with(authenticatedAs(EMPLOYEE_ID, "EMPLOYEE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.decisionComment").value(reason));
    }

    @Test
    void rejectionRequiresANonBlankCommentWithoutChangingData() throws Exception {
        long vacationId = leaveTypeId("Vacation");
        long requestId = insertRequest(EMPLOYEE_ID, MANAGER_ID, vacationId, "2026-10-29", "2026-11-03");
        reserve(EMPLOYEE_ID, vacationId, 4);

        mockMvc.perform(post("/approvals/{requestId}/reject", requestId)
                        .with(authenticatedAs(MANAGER_ID, "MANAGER"))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMENT_REQUIRED"));
        mockMvc.perform(post("/approvals/{requestId}/reject", requestId)
                        .with(authenticatedAs(MANAGER_ID, "MANAGER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":null}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMENT_REQUIRED"));
        mockMvc.perform(post("/approvals/{requestId}/reject", requestId)
                        .with(authenticatedAs(MANAGER_ID, "MANAGER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMENT_REQUIRED"));
        mockMvc.perform(post("/approvals/{requestId}/reject", requestId)
                        .with(authenticatedAs(MANAGER_ID, "MANAGER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\" \\t \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMENT_REQUIRED"));

        assertThat(jdbcTemplate.queryForObject(
                "select status from leave_requests where id = ?", String.class, requestId)).isEqualTo("PENDING");
        assertBalance(EMPLOYEE_ID, vacationId, 20, 4);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from balance_ledger where request_id = ?", Long.class, requestId)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from audit_events where entity_id = ? and action = 'LEAVE_REQUEST_REJECTED'",
                Long.class, requestId)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from notifications where request_id = ?", Long.class, requestId)).isZero();
    }

    @Test
    void rejectionUsesAssignedApproverAndPendingStateGuards() throws Exception {
        long vacationId = leaveTypeId("Vacation");
        long requestId = insertRequest(EMPLOYEE_ID, MANAGER_ID, vacationId, "2026-10-29", "2026-11-03");
        reserve(EMPLOYEE_ID, vacationId, 4);

        mockMvc.perform(post("/approvals/{requestId}/reject", requestId)
                        .header("X-Forwarded-Prefix", "/api")
                        .with(authenticatedAs(OTHER_MANAGER_ID, "MANAGER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"Not my request.\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        mockMvc.perform(post("/approvals/{requestId}/reject", requestId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"Unauthenticated.\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/approvals/{requestId}/reject", 999999L)
                        .with(authenticatedAs(MANAGER_ID, "MANAGER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"Unknown request.\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        jdbcTemplate.update("update leave_requests set status = 'APPROVED' where id = ?", requestId);
        mockMvc.perform(post("/approvals/{requestId}/reject", requestId)
                        .with(authenticatedAs(MANAGER_ID, "MANAGER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"Already decided.\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_TRANSITION"))
                .andExpect(jsonPath("$.detail").value(
                        "Request " + requestId + " is APPROVED and can no longer be rejected."));

        assertThat(jdbcTemplate.queryForObject("""
                select count(*) from access_log
                 where actor_id = ? and resource_id = ? and allowed = false
                   and resource like '%/reject'
                """, Long.class, OTHER_MANAGER_ID, requestId)).isEqualTo(1);
        assertBalance(EMPLOYEE_ID, vacationId, 20, 4);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from balance_ledger where request_id = ?", Long.class, requestId)).isZero();
        assertThat(jdbcTemplate.queryForObject("""
                select count(*) from audit_events
                 where entity_id = ? and action = 'LEAVE_REQUEST_REJECTED'
                """, Long.class, requestId)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from notifications where request_id = ?", Long.class, requestId)).isZero();
    }

    @Test
    void unsupportedDecisionCannotRejectWithoutAReason() {
        long vacationId = leaveTypeId("Vacation");
        long requestId = insertRequest(EMPLOYEE_ID, MANAGER_ID, vacationId, "2026-10-29", "2026-11-03");
        reserve(EMPLOYEE_ID, vacationId, 4);

        assertThatThrownBy(() -> leaveRequestService.applyDecision(requestId, null, null, MANAGER_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unsupported decision: null");

        assertThat(jdbcTemplate.queryForObject(
                "select status from leave_requests where id = ?", String.class, requestId)).isEqualTo("PENDING");
        assertBalance(EMPLOYEE_ID, vacationId, 20, 4);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from balance_ledger where request_id = ?", Long.class, requestId)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from audit_events where entity_id = ? and action = 'LEAVE_REQUEST_REJECTED'",
                Long.class, requestId)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from notifications where request_id = ?", Long.class, requestId)).isZero();
    }

    @Test
    void rejectionNotificationFailureDoesNotUndoCommittedDecision() throws Exception {
        long vacationId = leaveTypeId("Vacation");
        long requestId = insertRequest(EMPLOYEE_ID, MANAGER_ID, vacationId, "2026-10-29", "2026-11-03");
        reserve(EMPLOYEE_ID, vacationId, 4);
        jdbcTemplate.execute("""
                create function fail_rejected_notification() returns trigger as $$
                begin
                    if new.kind = 'REJECTED' then
                        raise exception 'forced notification failure';
                    end if;
                    return new;
                end;
                $$ language plpgsql
                """);
        jdbcTemplate.execute("""
                create trigger fail_rejected_notification
                before insert on notifications
                for each row execute function fail_rejected_notification()
                """);

        try {
            mockMvc.perform(post("/approvals/{requestId}/reject", requestId)
                            .with(authenticatedAs(MANAGER_ID, "MANAGER"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"comment\":\"Please resubmit later.\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("REJECTED"));
        } finally {
            jdbcTemplate.execute("drop trigger if exists fail_rejected_notification on notifications");
            jdbcTemplate.execute("drop function if exists fail_rejected_notification()");
        }

        assertBalance(EMPLOYEE_ID, vacationId, 20, 0);
        assertThat(jdbcTemplate.queryForObject("""
                select count(*) from audit_events
                 where entity_id = ? and action = 'LEAVE_REQUEST_REJECTED'
                """, Long.class, requestId)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("""
                select count(*) from balance_ledger where request_id = ? and kind = 'RELEASE'
                """, Long.class, requestId)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from notifications where request_id = ?", Long.class, requestId)).isZero();
    }

    @Test
    void hrCanApproveOwnAssignedRequestWithoutComment() throws Exception {
        long vacationId = leaveTypeId("Vacation");
        long requestId = insertRequest(HR_ID, HR_ID, vacationId, "2026-11-02", "2026-11-05");
        reserve(HR_ID, vacationId, 4);

        mockMvc.perform(post("/approvals/{requestId}/approve", requestId)
                        .with(authenticatedAs(HR_ID, "HR_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.approverId").value(HR_ID))
                .andExpect(jsonPath("$.decidedAt").isNotEmpty())
                .andExpect(jsonPath("$.decisionComment").value((Object) null));

        assertBalance(HR_ID, vacationId, 16, 0);
        assertThat(jdbcTemplate.queryForObject(
                "select decision_comment from leave_requests where id = ?", String.class, requestId)).isNull();
        assertThat(jdbcTemplate.queryForObject("""
                select count(*) from balance_ledger where request_id = ? and kind = 'DEDUCT'
                """, Long.class, requestId)).isEqualTo(1);
    }

    @Test
    void notificationFailureDoesNotUndoCommittedApproval() throws Exception {
        long vacationId = leaveTypeId("Vacation");
        long requestId = insertRequest(EMPLOYEE_ID, MANAGER_ID, vacationId, "2026-10-29", "2026-11-03");
        reserve(EMPLOYEE_ID, vacationId, 4);
        jdbcTemplate.execute("""
                create function fail_approved_notification() returns trigger as $$
                begin
                    if new.kind = 'APPROVED' then
                        raise exception 'forced notification failure';
                    end if;
                    return new;
                end;
                $$ language plpgsql
                """);
        jdbcTemplate.execute("""
                create trigger fail_approved_notification
                before insert on notifications
                for each row execute function fail_approved_notification()
                """);

        try {
            mockMvc.perform(post("/approvals/{requestId}/approve", requestId)
                            .with(authenticatedAs(MANAGER_ID, "MANAGER")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("APPROVED"));
        } finally {
            jdbcTemplate.execute("drop trigger if exists fail_approved_notification on notifications");
            jdbcTemplate.execute("drop function if exists fail_approved_notification()");
        }

        assertBalance(EMPLOYEE_ID, vacationId, 16, 0);
        assertThat(jdbcTemplate.queryForObject("""
                select count(*) from audit_events
                 where entity_id = ? and action = 'LEAVE_REQUEST_APPROVED'
                """, Long.class, requestId)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from balance_ledger where request_id = ? and kind = 'DEDUCT'",
                Long.class, requestId)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from notifications where request_id = ?", Long.class, requestId)).isZero();
    }

    @Test
    void nonPendingRequestCannotBeApprovedAgain() throws Exception {
        long vacationId = leaveTypeId("Vacation");
        long requestId = insertRequest(EMPLOYEE_ID, MANAGER_ID, vacationId, "2026-10-29", "2026-11-03");
        reserve(EMPLOYEE_ID, vacationId, 4);
        jdbcTemplate.update("update leave_requests set status = 'APPROVED' where id = ?", requestId);

        mockMvc.perform(post("/approvals/{requestId}/approve", requestId)
                        .with(authenticatedAs(MANAGER_ID, "MANAGER")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_TRANSITION"))
                .andExpect(jsonPath("$.detail").value(
                        "Request " + requestId + " is APPROVED and can no longer be approved."));

        assertBalance(EMPLOYEE_ID, vacationId, 20, 4);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from balance_ledger where request_id = ?", Long.class, requestId)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from audit_events where entity_id = ? and action = 'LEAVE_REQUEST_APPROVED'",
                Long.class, requestId)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from notifications where request_id = ?", Long.class, requestId)).isZero();
    }

    @Test
    void wrongApproverIsDeniedAndLoggedWithoutChangingRequestOrBalance() throws Exception {
        long vacationId = leaveTypeId("Vacation");
        long requestId = insertRequest(EMPLOYEE_ID, MANAGER_ID, vacationId, "2026-10-29", "2026-11-03");
        reserve(EMPLOYEE_ID, vacationId, 4);

        mockMvc.perform(post("/approvals/{requestId}/approve", requestId)
                        .header("X-Forwarded-Prefix", "/api")
                        .with(authenticatedAs(OTHER_MANAGER_ID, "MANAGER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        assertBalance(EMPLOYEE_ID, vacationId, 20, 4);
        assertThat(jdbcTemplate.queryForObject("""
                select count(*) from access_log
                 where actor_id = ? and resource_id = ? and allowed = false
                   and resource like '%/approve'
                """, Long.class, OTHER_MANAGER_ID, requestId))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from balance_ledger where request_id = ?", Long.class, requestId)).isZero();
    }

    @Test
    void unauthenticatedAndUnknownRequestApprovalsDoNotChangeData() throws Exception {
        long vacationId = leaveTypeId("Vacation");
        long requestId = insertRequest(EMPLOYEE_ID, MANAGER_ID, vacationId, "2026-10-29", "2026-11-03");
        reserve(EMPLOYEE_ID, vacationId, 4);

        mockMvc.perform(post("/approvals/{requestId}/approve", requestId))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/approvals/{requestId}/approve", 999999L)
                        .with(authenticatedAs(MANAGER_ID, "MANAGER")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        assertBalance(EMPLOYEE_ID, vacationId, 20, 4);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from balance_ledger where request_id = ?", Long.class, requestId)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from audit_events where entity_id = ? and action = 'LEAVE_REQUEST_APPROVED'",
                Long.class, requestId)).isZero();
    }

    private long insertRequest(long employeeId, long approverId, long leaveTypeId, String startDate, String endDate) {
        return jdbcTemplate.queryForObject("""
                insert into leave_requests
                    (employee_id, approver_id, leave_type_id, start_date, end_date,
                     working_days, notes, status, submitted_at)
                values (?, ?, ?, cast(? as date), cast(? as date), 4, 'Dashboard test', 'PENDING', current_timestamp)
                returning id
                """, Long.class, employeeId, approverId, leaveTypeId, startDate, endDate);
    }

    private void reserve(long employeeId, long leaveTypeId, int days) {
        jdbcTemplate.update("""
                update leave_balances
                   set reserved_days = ?
                 where user_id = ? and leave_type_id = ?
                """, days, employeeId, leaveTypeId);
    }

    private void assertBalance(long userId, long leaveTypeId, int totalDays, int reservedDays) {
        assertThat(jdbcTemplate.queryForObject("""
                select total_days from leave_balances where user_id = ? and leave_type_id = ?
                """, Integer.class, userId, leaveTypeId)).isEqualTo(totalDays);
        assertThat(jdbcTemplate.queryForObject("""
                select reserved_days from leave_balances where user_id = ? and leave_type_id = ?
                """, Integer.class, userId, leaveTypeId)).isEqualTo(reservedDays);
    }

    private void insertHistory(
            long requestId, String action, long actorId, String detailsJson, Instant createdAt) {
        insertHistoryForEntity(requestId, action, actorId, detailsJson, createdAt);
    }

    private void insertHistoryForEntity(
            long entityId, String action, long actorId, String detailsJson, Instant createdAt) {
        jdbcTemplate.update("""
                insert into audit_events (entity, entity_id, action, actor_id, details_json, created_at)
                values (?, ?, ?, ?, cast(? as jsonb), ?)
                """,
                action.equals("REQUEST_COMMENT_ADDED") ? "REQUEST_COMMENT" : "LEAVE_REQUEST",
                entityId, action, actorId, detailsJson, Timestamp.from(createdAt));
    }

    private long leaveTypeId(String name) {
        return jdbcTemplate.queryForObject("select id from leave_types where name = ?", Long.class, name);
    }

    private RequestPostProcessor authenticatedAs(long userId, String role) {
        return authentication(new TestingAuthenticationToken(
                userId, null, new SimpleGrantedAuthority("ROLE_" + role)));
    }
}
