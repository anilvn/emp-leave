package com.company.leave.users;

import javax.sql.DataSource;

import java.util.List;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Disabled("Temporarily disabled")
class UserControllerIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18");

    private static final long HR_ADMIN_ID = 1L;
    private static final long MANAGER_ID = 2L;
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
        jdbcTemplate.update(
                "update users set must_change_password = false, "
                        + "password_hash = '$2a$10$494KRTD7NLmmrKANwVPvQ.xFiOxOpaw9eS4p3hWmzs8GFT2ssDIri' "
                        + "where id = ?",
                EMPLOYEE_ID);
    }

    @Test
    void hrCreatesEmployeeReturns201WithAuditEventAndNoPlaintextPasswordInResponse() throws Exception {
        String body = """
                {"username":"new.emp.one","role":"EMPLOYEE","managerId":3,"temporaryPassword":"Temp#2026"}
                """;

        MvcResult result = mockMvc.perform(post("/users")
                        .with(authenticatedAs(HR_ADMIN_ID, "HR_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("new.emp.one"))
                .andExpect(jsonPath("$.role").value("EMPLOYEE"))
                .andExpect(jsonPath("$.managerId").value(3))
                .andExpect(jsonPath("$.mustChangePassword").value(true))
                .andExpect(jsonPath("$.active").value(true))
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        assertThat(responseBody).doesNotContain("Temp#2026").doesNotContain("temporaryPassword");

        Long createdId = jdbcTemplate.queryForObject(
                "select id from users where username = ?", Long.class, "new.emp.one");
        String passwordHash = jdbcTemplate.queryForObject(
                "select password_hash from users where id = ?", String.class, createdId);
        assertThat(passwordHash).isNotEqualTo("Temp#2026").startsWith("$2");

        assertThat(jdbcTemplate.queryForObject(
                        "select count(*) from audit_events where entity = 'USER' and action = 'CREATE' "
                                + "and entity_id = ? and actor_id = ? and created_at is not null",
                        Integer.class,
                        createdId,
                        HR_ADMIN_ID))
                .isEqualTo(1);
    }

    @Test
    void hrCreatesHrAdminWithoutManagerIdReturns201WithNullManagerId() throws Exception {
        String body = """
                {"username":"new.hr.one","role":"HR_ADMIN","managerId":null,"temporaryPassword":"Temp#2026"}
                """;

        mockMvc.perform(post("/users")
                        .with(authenticatedAs(HR_ADMIN_ID, "HR_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("HR_ADMIN"))
                .andExpect(jsonPath("$.managerId").value(Matchers.nullValue()));
    }

    @Test
    void createEmployeeWithoutManagerIdIsRejected() throws Exception {
        String body = """
                {"username":"new.emp.two","role":"EMPLOYEE","managerId":null,"temporaryPassword":"Temp#2026"}
                """;

        mockMvc.perform(post("/users")
                        .with(authenticatedAs(HR_ADMIN_ID, "HR_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        assertThat(jdbcTemplate.queryForObject(
                        "select count(*) from users where username = ?", Integer.class, "new.emp.two"))
                .isZero();
    }

    @Test
    void managerIdPointingToNonManagerIsRejected() throws Exception {
        String body = """
                {"username":"new.emp.three","role":"EMPLOYEE","managerId":5,"temporaryPassword":"Temp#2026"}
                """;

        mockMvc.perform(post("/users")
                        .with(authenticatedAs(HR_ADMIN_ID, "HR_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        assertThat(jdbcTemplate.queryForObject(
                        "select count(*) from users where username = ?", Integer.class, "new.emp.three"))
                .isZero();
    }

    @Test
    void managerIdPointingToNonexistentUserIsRejected() throws Exception {
        String body = """
                {"username":"new.emp.four","role":"EMPLOYEE","managerId":9999,"temporaryPassword":"Temp#2026"}
                """;

        mockMvc.perform(post("/users")
                        .with(authenticatedAs(HR_ADMIN_ID, "HR_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        assertThat(jdbcTemplate.queryForObject(
                        "select count(*) from users where username = ?", Integer.class, "new.emp.four"))
                .isZero();
    }

    @Test
    void duplicateUsernameIsRejectedAndNoRowIsCreated() throws Exception {
        int before = jdbcTemplate.queryForObject("select count(*) from users", Integer.class);
        String body = """
                {"username":"emp.jsmith","role":"EMPLOYEE","managerId":3,"temporaryPassword":"Temp#2026"}
                """;

        mockMvc.perform(post("/users")
                        .with(authenticatedAs(HR_ADMIN_ID, "HR_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        int after = jdbcTemplate.queryForObject("select count(*) from users", Integer.class);
        assertThat(after).isEqualTo(before);
    }

    @Test
    void nonHrCallerIsDeniedOnAllThreeEndpoints() throws Exception {
        String createBody = """
                {"username":"blocked.create","role":"EMPLOYEE","managerId":3,"temporaryPassword":"Temp#2026"}
                """;

        mockMvc.perform(post("/users")
                        .with(authenticatedAs(EMPLOYEE_ID, "EMPLOYEE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        mockMvc.perform(post("/users")
                        .with(authenticatedAs(MANAGER_ID, "MANAGER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        mockMvc.perform(get("/users").with(authenticatedAs(EMPLOYEE_ID, "EMPLOYEE")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        mockMvc.perform(get("/users").with(authenticatedAs(MANAGER_ID, "MANAGER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        mockMvc.perform(get("/users/managers").with(authenticatedAs(EMPLOYEE_ID, "EMPLOYEE")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        mockMvc.perform(get("/users/managers").with(authenticatedAs(MANAGER_ID, "MANAGER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        assertThat(jdbcTemplate.queryForObject(
                        "select count(*) from users where username = ?", Integer.class, "blocked.create"))
                .isZero();
    }

    @Test
    void listUsersReturnsEveryAccountWithNoScopeFilter() throws Exception {
        int expectedCount = jdbcTemplate.queryForObject("select count(*) from users", Integer.class);

        mockMvc.perform(get("/users").with(authenticatedAs(HR_ADMIN_ID, "HR_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(expectedCount))
                .andExpect(jsonPath("$[*].username").value(Matchers.hasItem("hr.admin")))
                .andExpect(jsonPath("$[*].username").value(Matchers.hasItem("emp.jsmith")));
    }

    @Test
    void listManagerOptionsReturnsEveryManagerAndHrAdmin() throws Exception {
        int expectedCount = jdbcTemplate.queryForObject(
                "select count(*) from users where role in ('MANAGER', 'HR_ADMIN')", Integer.class);

        mockMvc.perform(get("/users/managers").with(authenticatedAs(HR_ADMIN_ID, "HR_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(expectedCount))
                .andExpect(jsonPath("$[*].role").value(
                        Matchers.everyItem(Matchers.isIn(List.of("MANAGER", "HR_ADMIN")))))
                .andExpect(jsonPath("$[*].username").value(Matchers.hasItem("mgr.chen")));
    }

    @Test
    void hrResetsExistingUserReturns200WithGeneratedPasswordRevokesSessionsAndWritesAudit() throws Exception {
        jdbcTemplate.update(
                "insert into active_sessions (token_id, user_id, last_seen_at, expires_at) "
                        + "values (?, ?, now(), now() + interval '15 minutes')",
                "reset-password-test-token",
                EMPLOYEE_ID);
        String originalHash = jdbcTemplate.queryForObject(
                "select password_hash from users where id = ?", String.class, EMPLOYEE_ID);

        MvcResult result = mockMvc.perform(post("/users/{userId}/reset-password", EMPLOYEE_ID)
                        .with(authenticatedAs(HR_ADMIN_ID, "HR_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.temporaryPassword").isNotEmpty())
                .andReturn();

        String temporaryPassword = com.jayway.jsonpath.JsonPath.read(
                result.getResponse().getContentAsString(), "$.temporaryPassword");
        assertThat(temporaryPassword).isNotBlank();
        assertThat(result.getResponse().getContentAsString()).doesNotContain(originalHash);

        assertThat(jdbcTemplate.queryForObject(
                "select must_change_password from users where id = ?", Boolean.class, EMPLOYEE_ID)).isTrue();
        String newHash = jdbcTemplate.queryForObject(
                "select password_hash from users where id = ?", String.class, EMPLOYEE_ID);
        assertThat(newHash).isNotEqualTo(originalHash).startsWith("$2");
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from active_sessions where user_id = ?", Integer.class, EMPLOYEE_ID))
                .isZero();
        assertThat(jdbcTemplate.queryForObject(
                        "select count(*) from audit_events where entity = 'USER' and action = 'RESET_PASSWORD' "
                                + "and entity_id = ? and actor_id = ? and created_at is not null",
                        Integer.class,
                        EMPLOYEE_ID,
                        HR_ADMIN_ID))
                .isEqualTo(1);
    }

    @Test
    void hrResetsUnknownUserReturns404WithoutWritingAnyRow() throws Exception {
        long unknownUserId = 999999L;
        int auditBefore = jdbcTemplate.queryForObject(
                "select count(*) from audit_events where entity = 'USER' and action = 'RESET_PASSWORD'",
                Integer.class);

        mockMvc.perform(post("/users/{userId}/reset-password", unknownUserId)
                        .with(authenticatedAs(HR_ADMIN_ID, "HR_ADMIN")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from audit_events where entity = 'USER' and action = 'RESET_PASSWORD'",
                Integer.class)).isEqualTo(auditBefore);
    }

    @Test
    void nonHrResetAttemptIsDeniedAndWritesDeniedAccessLogRow() throws Exception {
        String resource = "POST /api/users/" + EMPLOYEE_ID + "/reset-password";
        int before = jdbcTemplate.queryForObject(
                "select count(*) from access_log where resource = ? and actor_id = ? and allowed = false",
                Integer.class, resource, MANAGER_ID);
        String originalHash = jdbcTemplate.queryForObject(
                "select password_hash from users where id = ?", String.class, EMPLOYEE_ID);

        mockMvc.perform(post("/users/{userId}/reset-password", EMPLOYEE_ID)
                        .with(authenticatedAs(MANAGER_ID, "MANAGER"))
                        .header("X-Forwarded-Prefix", "/api"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        assertThat(jdbcTemplate.queryForObject(
                "select password_hash from users where id = ?", String.class, EMPLOYEE_ID))
                .isEqualTo(originalHash);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from access_log where resource = ? and actor_id = ? and allowed = false",
                Integer.class, resource, MANAGER_ID))
                .isEqualTo(before + 1);
    }

    private RequestPostProcessor authenticatedAs(long actorId, String role) {
        return authentication(new TestingAuthenticationToken(actorId, null, new SimpleGrantedAuthority("ROLE_" + role)));
    }
}
