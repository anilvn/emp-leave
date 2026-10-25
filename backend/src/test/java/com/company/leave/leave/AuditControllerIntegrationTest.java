package com.company.leave.leave;

import java.sql.Timestamp;
import java.time.Instant;
import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class AuditControllerIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18");

    private static final long HR_ID = 1L;
    private static final long MANAGER_ID = 3L;
    private static final long EMPLOYEE_ID = 5L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DataSource dataSource;

    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.update("delete from active_sessions");
        jdbcTemplate.update("delete from notifications");
        jdbcTemplate.update("delete from balance_ledger");
        jdbcTemplate.update("delete from request_comments");
        jdbcTemplate.update("delete from leave_requests");
        jdbcTemplate.update("update users set must_change_password = false where id in (?, ?, ?)",
                HR_ID, MANAGER_ID, EMPLOYEE_ID);
    }

    @Test
    void hrGetsAuditReportWithAllStatusesNewestFirst() throws Exception {
        long vacationId = leaveTypeId("Vacation");
        // oldest submission
        long cancelledReq = insertRequest(EMPLOYEE_ID, MANAGER_ID, vacationId, "CANCELLED",
                Instant.parse("2026-08-01T08:00:00Z"));
        long pendingReq = insertRequest(EMPLOYEE_ID, MANAGER_ID, vacationId, "PENDING",
                Instant.parse("2026-09-01T08:00:00Z"));
        long approvedReq = insertRequest(EMPLOYEE_ID, MANAGER_ID, vacationId, "APPROVED",
                Instant.parse("2026-09-15T08:00:00Z"));
        // newest submission
        long rejectedReq = insertRequest(EMPLOYEE_ID, MANAGER_ID, vacationId, "REJECTED",
                Instant.parse("2026-10-01T08:00:00Z"));

        mockMvc.perform(get("/audit")
                        .with(authenticatedAs(HR_ID, "HR_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[0].requestId").value(rejectedReq))
                .andExpect(jsonPath("$[0].status").value("REJECTED"))
                .andExpect(jsonPath("$[1].requestId").value(approvedReq))
                .andExpect(jsonPath("$[1].status").value("APPROVED"))
                .andExpect(jsonPath("$[2].requestId").value(pendingReq))
                .andExpect(jsonPath("$[2].status").value("PENDING"))
                .andExpect(jsonPath("$[3].requestId").value(cancelledReq))
                .andExpect(jsonPath("$[3].status").value("CANCELLED"))
                .andExpect(jsonPath("$[0].employeeUsername").value("emp.jsmith"))
                .andExpect(jsonPath("$[0].leaveTypeName").value("Vacation"));
    }

    @Test
    void hrGetsAuditReportWithNestedCommentsAndHistory() throws Exception {
        long vacationId = leaveTypeId("Vacation");
        long requestId = insertRequest(EMPLOYEE_ID, MANAGER_ID, vacationId, "APPROVED",
                Instant.parse("2026-10-01T09:00:00Z"));
        long commentId = insertComment(requestId, MANAGER_ID, "Approved, enjoy your break.",
                Instant.parse("2026-10-02T10:00:00Z"));
        insertAuditEvent(requestId, "LEAVE_REQUEST", "LEAVE_REQUEST_SUBMITTED", EMPLOYEE_ID,
                "{\"detail\":\"Submitted Vacation, 2026-10-06 to 2026-10-10 (5 working days).\"}",
                Instant.parse("2026-10-01T09:00:00Z"));
        insertAuditEvent(requestId, "LEAVE_REQUEST", "LEAVE_REQUEST_APPROVED", MANAGER_ID,
                "{\"detail\":\"Approved, enjoy your break.\"}",
                Instant.parse("2026-10-02T10:00:00Z"));
        insertAuditEvent(commentId, "REQUEST_COMMENT", "REQUEST_COMMENT_ADDED", MANAGER_ID,
                "{\"requestId\":" + requestId + ",\"comment\":\"Approved, enjoy your break.\"}",
                Instant.parse("2026-10-02T10:00:00Z"));

        mockMvc.perform(get("/audit")
                        .with(authenticatedAs(HR_ID, "HR_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].requestId").value(requestId))
                .andExpect(jsonPath("$[0].comments.length()").value(1))
                .andExpect(jsonPath("$[0].comments[0].body").value("Approved, enjoy your break."))
                .andExpect(jsonPath("$[0].comments[0].authorId").value(MANAGER_ID))
                .andExpect(jsonPath("$[0].history.length()").value(3))
                .andExpect(jsonPath("$[0].history[0].type").value("SUBMITTED"))
                .andExpect(jsonPath("$[0].history[1].type").value("APPROVED"))
                .andExpect(jsonPath("$[0].history[2].type").value("COMMENT"));
    }

    @Test
    void emptyArrayWhenNoRequestsExist() throws Exception {
        mockMvc.perform(get("/audit")
                        .with(authenticatedAs(HR_ID, "HR_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void employeeIsRefusedWithAccessDenied() throws Exception {
        mockMvc.perform(get("/audit")
                        .header("X-Forwarded-Prefix", "/api")
                        .with(authenticatedAs(EMPLOYEE_ID, "EMPLOYEE")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        assertThat(jdbcTemplate.queryForObject("""
                select count(*) from access_log
                 where actor_id = ? and allowed = false
                   and resource like '%/audit'
                """, Long.class, EMPLOYEE_ID)).isEqualTo(1);
    }

    @Test
    void managerIsRefusedWithAccessDenied() throws Exception {
        mockMvc.perform(get("/audit")
                        .header("X-Forwarded-Prefix", "/api")
                        .with(authenticatedAs(MANAGER_ID, "MANAGER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        assertThat(jdbcTemplate.queryForObject("""
                select count(*) from access_log
                 where actor_id = ? and allowed = false
                   and resource like '%/audit'
                """, Long.class, MANAGER_ID)).isEqualTo(1);
    }

    @Test
    void unauthenticatedRequestIsUnauthorized() throws Exception {
        mockMvc.perform(get("/audit"))
                .andExpect(status().isUnauthorized());
    }

    private long insertRequest(long employeeId, long approverId, long leaveTypeId,
            String status, Instant submittedAt) {
        return jdbcTemplate.queryForObject("""
                insert into leave_requests
                    (employee_id, approver_id, leave_type_id, start_date, end_date,
                     working_days, status, submitted_at)
                values (?, ?, ?, '2026-10-06', '2026-10-10', 5, ?, ?)
                returning id
                """, Long.class, employeeId, approverId, leaveTypeId, status,
                Timestamp.from(submittedAt));
    }

    private long insertComment(long requestId, long authorId, String body, Instant createdAt) {
        return jdbcTemplate.queryForObject("""
                insert into request_comments (request_id, author_id, body, created_at)
                values (?, ?, ?, ?)
                returning id
                """, Long.class, requestId, authorId, body, Timestamp.from(createdAt));
    }

    private void insertAuditEvent(long entityId, String entity, String action, long actorId,
            String detailsJson, Instant createdAt) {
        jdbcTemplate.update("""
                insert into audit_events (entity, entity_id, action, actor_id, details_json, created_at)
                values (?, ?, ?, ?, cast(? as jsonb), ?)
                """, entity, entityId, action, actorId, detailsJson, Timestamp.from(createdAt));
    }

    private long leaveTypeId(String name) {
        return jdbcTemplate.queryForObject("select id from leave_types where name = ?", Long.class, name);
    }

    private RequestPostProcessor authenticatedAs(long userId, String role) {
        return authentication(new TestingAuthenticationToken(
                userId, null, new SimpleGrantedAuthority("ROLE_" + role)));
    }
}
