package com.company.leave.team;

import java.sql.Timestamp;
import java.time.Instant;
import javax.sql.DataSource;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class TeamLeaveControllerIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18");

    private static final long HR_ID = 1L;
    private static final long MANAGER_ID = 3L;
    private static final long OTHER_MANAGER_ID = 4L;
    private static final long EMPLOYEE_ID = 5L;
    private static final long EMPLOYEE2_ID = 9L; // emp.emurphy, reports to OTHER_MANAGER_ID=4

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
                HR_ID, MANAGER_ID, OTHER_MANAGER_ID, EMPLOYEE_ID, EMPLOYEE2_ID);
    }

    @Test
    void managerSeesOnlyOwnTeamsApprovedLeaveWithCommentsAndHistory() throws Exception {
        long vacationId = leaveTypeId("Vacation");
        // APPROVED request for MANAGER's direct report
        long approvedRequest = insertApprovedRequest(
                EMPLOYEE_ID, MANAGER_ID, vacationId, "2026-10-29", "2026-11-03");
        // PENDING request — must not appear
        insertRequest(EMPLOYEE_ID, MANAGER_ID, vacationId, "2026-11-10", "2026-11-11");
        // Another team's APPROVED request — must not appear
        insertApprovedRequest(EMPLOYEE2_ID, OTHER_MANAGER_ID, vacationId, "2026-10-29", "2026-11-03");

        long commentId = insertComment(approvedRequest, MANAGER_ID, "Looks good.", Instant.parse("2026-10-22T10:00:00Z"));
        insertHistoryForEntity(approvedRequest, "LEAVE_REQUEST", "LEAVE_REQUEST_SUBMITTED", EMPLOYEE_ID,
                "{\"detail\":\"Submitted Vacation, 2026-10-29 to 2026-11-03 (4 working days).\"}",
                Instant.parse("2026-10-20T09:00:00Z"));
        insertHistoryForEntity(approvedRequest, "LEAVE_REQUEST", "LEAVE_REQUEST_APPROVED", MANAGER_ID,
                "{\"detail\":\"Approved request.\"}",
                Instant.parse("2026-10-21T09:00:00Z"));
        insertHistoryForEntity(commentId, "REQUEST_COMMENT", "REQUEST_COMMENT_ADDED", MANAGER_ID,
                "{\"requestId\":" + approvedRequest + ",\"comment\":\"Looks good.\"}",
                Instant.parse("2026-10-22T10:00:00Z"));

        MvcResult result = mockMvc.perform(get("/team/leave")
                        .with(authenticatedAs(MANAGER_ID, "MANAGER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].requestId").value(approvedRequest))
                .andExpect(jsonPath("$[0].employeeId").value(EMPLOYEE_ID))
                .andExpect(jsonPath("$[0].employeeUsername").value("emp.jsmith"))
                .andExpect(jsonPath("$[0].status").value("APPROVED"))
                .andExpect(jsonPath("$[0].startDate").value("2026-10-29"))
                .andExpect(jsonPath("$[0].endDate").value("2026-11-03"))
                .andExpect(jsonPath("$[0].workingDays").value(4))
                .andExpect(jsonPath("$[0].comments.length()").value(1))
                .andExpect(jsonPath("$[0].comments[0].body").value("Looks good."))
                .andExpect(jsonPath("$[0].comments[0].authorId").value(MANAGER_ID))
                .andExpect(jsonPath("$[0].history.length()").value(3))
                .andExpect(jsonPath("$[0].history[0].type").value("SUBMITTED"))
                .andExpect(jsonPath("$[0].history[1].type").value("APPROVED"))
                .andExpect(jsonPath("$[0].history[2].type").value("COMMENT"))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat((Number) JsonPath.read(body, "$[0].comments[0].id"))
                .extracting(Number::longValue).isEqualTo(commentId);
    }

    @Test
    void dateRangeFilterIncludesOnlyOverlappingRequests() throws Exception {
        long vacationId = leaveTypeId("Vacation");
        // Overlaps Aug: starts July, ends Aug 5
        long overlapsStart = insertApprovedRequest(EMPLOYEE_ID, MANAGER_ID, vacationId, "2026-07-28", "2026-08-05");
        // Fully within Aug
        long withinRange = insertApprovedRequest(EMPLOYEE_ID, MANAGER_ID, vacationId, "2026-08-10", "2026-08-14");
        // Overlaps end: starts Aug 29, ends Sep 3
        long overlapsEnd = insertApprovedRequest(EMPLOYEE_ID, MANAGER_ID, vacationId, "2026-08-29", "2026-09-03");
        // Entirely before Aug — must not appear
        insertApprovedRequest(EMPLOYEE_ID, MANAGER_ID, vacationId, "2026-07-01", "2026-07-25");
        // Entirely after Aug — must not appear
        insertApprovedRequest(EMPLOYEE_ID, MANAGER_ID, vacationId, "2026-09-10", "2026-09-15");

        mockMvc.perform(get("/team/leave")
                        .param("from", "2026-08-01")
                        .param("to", "2026-08-31")
                        .with(authenticatedAs(MANAGER_ID, "MANAGER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].requestId").value(overlapsStart))
                .andExpect(jsonPath("$[1].requestId").value(withinRange))
                .andExpect(jsonPath("$[2].requestId").value(overlapsEnd));
    }

    @Test
    void omittedDateParamsReturnAllApprovedTeamLeave() throws Exception {
        long vacationId = leaveTypeId("Vacation");
        long req1 = insertApprovedRequest(EMPLOYEE_ID, MANAGER_ID, vacationId, "2026-01-05", "2026-01-09");
        long req2 = insertApprovedRequest(EMPLOYEE_ID, MANAGER_ID, vacationId, "2026-12-01", "2026-12-05");

        mockMvc.perform(get("/team/leave")
                        .with(authenticatedAs(MANAGER_ID, "MANAGER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].requestId").value(req1))
                .andExpect(jsonPath("$[1].requestId").value(req2));
    }

    @Test
    void noApprovedReportsReturnsEmptyArray() throws Exception {
        mockMvc.perform(get("/team/leave")
                        .with(authenticatedAs(MANAGER_ID, "MANAGER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void employeeIsRefusedWithAccessDeniedAndAccessLogEntry() throws Exception {
        mockMvc.perform(get("/team/leave")
                        .header("X-Forwarded-Prefix", "/api")
                        .with(authenticatedAs(EMPLOYEE_ID, "EMPLOYEE")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        assertThat(jdbcTemplate.queryForObject("""
                select count(*) from access_log
                 where actor_id = ? and allowed = false
                   and resource like '%/team/leave'
                """, Long.class, EMPLOYEE_ID)).isEqualTo(1);
    }

    @Test
    void hrAdminIsRefusedWithAccessDenied() throws Exception {
        mockMvc.perform(get("/team/leave")
                        .header("X-Forwarded-Prefix", "/api")
                        .with(authenticatedAs(HR_ID, "HR_ADMIN")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        assertThat(jdbcTemplate.queryForObject("""
                select count(*) from access_log
                 where actor_id = ? and allowed = false
                   and resource like '%/team/leave'
                """, Long.class, HR_ID)).isEqualTo(1);
    }

    @Test
    void unauthenticatedRequestIsUnauthorized() throws Exception {
        mockMvc.perform(get("/team/leave"))
                .andExpect(status().isUnauthorized());
    }

    private long insertApprovedRequest(long employeeId, long approverId, long leaveTypeId,
            String startDate, String endDate) {
        return jdbcTemplate.queryForObject("""
                insert into leave_requests
                    (employee_id, approver_id, leave_type_id, start_date, end_date,
                     working_days, notes, status, submitted_at)
                values (?, ?, ?, cast(? as date), cast(? as date), 4, 'Team leave test', 'APPROVED', current_timestamp)
                returning id
                """, Long.class, employeeId, approverId, leaveTypeId, startDate, endDate);
    }

    private long insertRequest(long employeeId, long approverId, long leaveTypeId,
            String startDate, String endDate) {
        return jdbcTemplate.queryForObject("""
                insert into leave_requests
                    (employee_id, approver_id, leave_type_id, start_date, end_date,
                     working_days, notes, status, submitted_at)
                values (?, ?, ?, cast(? as date), cast(? as date), 2, 'Team leave test', 'PENDING', current_timestamp)
                returning id
                """, Long.class, employeeId, approverId, leaveTypeId, startDate, endDate);
    }

    private long insertComment(long requestId, long authorId, String body, Instant createdAt) {
        return jdbcTemplate.queryForObject("""
                insert into request_comments (request_id, author_id, body, created_at)
                values (?, ?, ?, ?)
                returning id
                """, Long.class, requestId, authorId, body, Timestamp.from(createdAt));
    }

    private void insertHistoryForEntity(long entityId, String entity, String action, long actorId,
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
