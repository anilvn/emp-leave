package com.company.leave.security;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Covers FR20's filter-level gate (matrix rows "Blocked while pending" and "Escape routes stay
 * open"), following {@code AccessLogFilterIntegrationTest}'s conventions: a throwaway controller
 * stands in for "every other endpoint", and the named escape routes are exercised directly.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Import(PasswordChangeRequiredFilterIntegrationTest.TestController.class)
class PasswordChangeRequiredFilterIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18");

    private static final long EMPLOYEE_ID = 5L;
    private static final String SEEDED_HASH =
            "$2a$10$494KRTD7NLmmrKANwVPvQ.xFiOxOpaw9eS4p3hWmzs8GFT2ssDIri";

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
                "update users set must_change_password = false, password_hash = ?, "
                        + "failed_logins = 0, locked_until = null where id = ?",
                SEEDED_HASH, EMPLOYEE_ID);
    }

    @Test
    void blockedWhilePendingReturnsForbiddenWithPasswordChangeRequiredCode() throws Exception {
        jdbcTemplate.update("update users set must_change_password = true where id = ?", EMPLOYEE_ID);

        mockMvc.perform(get("/password-gate-test").with(authenticatedAs(EMPLOYEE_ID)))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.valueOf("application/problem+json")))
                .andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_REQUIRED"))
                .andExpect(jsonPath("$.title").value("Password change required"));

        // A short-circuited 403 never reaches AccessLogFilter later in the chain, so this
        // filter must record its own denial.
        assertThat(jdbcTemplate.queryForObject(
                        "select count(*) from access_log where actor_id = ? and resource = ? and allowed = false",
                        Integer.class,
                        EMPLOYEE_ID,
                        "GET /password-gate-test"))
                .isEqualTo(1);
    }

    @Test
    void callerWithoutPendingChangeIsNotBlocked() throws Exception {
        mockMvc.perform(get("/password-gate-test").with(authenticatedAs(EMPLOYEE_ID)))
                .andExpect(status().isOk());
    }

    @Test
    void changePasswordEscapeRouteStaysOpenWhilePending() throws Exception {
        jdbcTemplate.update("update users set must_change_password = true where id = ?", EMPLOYEE_ID);

        mockMvc.perform(post("/auth/change-password")
                        .with(authenticatedAs(EMPLOYEE_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"Passw0rd1\",\"newPassword\":\"NewPass2\"}"))
                .andExpect(status().isNoContent());

        assertThat(jdbcTemplate.queryForObject(
                "select must_change_password from users where id = ?", Boolean.class, EMPLOYEE_ID)).isFalse();
    }

    @Test
    void logoutEscapeRouteStaysOpenWhilePending() throws Exception {
        jdbcTemplate.update("update users set must_change_password = true where id = ?", EMPLOYEE_ID);

        mockMvc.perform(post("/auth/logout").with(authenticatedAs(EMPLOYEE_ID)))
                .andExpect(status().isNoContent());
    }

    @Test
    void actuatorHealthEscapeRouteStaysOpenWhilePending() throws Exception {
        jdbcTemplate.update("update users set must_change_password = true where id = ?", EMPLOYEE_ID);

        mockMvc.perform(get("/actuator/health").with(authenticatedAs(EMPLOYEE_ID)))
                .andExpect(status().isOk());
    }

    @Test
    void unauthenticatedRequestIsNotBlockedByThisFilter() throws Exception {
        jdbcTemplate.update("update users set must_change_password = true where id = ?", EMPLOYEE_ID);

        mockMvc.perform(get("/password-gate-test"))
                .andExpect(status().isUnauthorized());
    }

    private RequestPostProcessor authenticatedAs(long actorId) {
        return authentication(new TestingAuthenticationToken(
                actorId, null, new SimpleGrantedAuthority("ROLE_EMPLOYEE")));
    }

    @RestController
    @RequestMapping("/password-gate-test")
    static class TestController {

        @GetMapping
        ResponseEntity<Void> probe() {
            return ResponseEntity.ok().build();
        }
    }
}
