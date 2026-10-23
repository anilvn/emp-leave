package com.company.leave.auth;

import jakarta.servlet.http.Cookie;
import javax.sql.DataSource;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import com.company.leave.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class AuthControllerIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18");

    private static final String LOGIN_BODY = """
            {"username":"emp.jsmith","password":"Passw0rd1"}
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JwtService jwtService;

    private JdbcTemplate jdbcTemplate;
    private final java.util.Map<String, Integer> initialAuditCounts = new java.util.HashMap<>();

    @BeforeEach
    void prepareDatabase() {
        jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.update("delete from active_sessions");
        jdbcTemplate.update(
                "update users set active = true, failed_logins = 0, locked_until = null, "
                        + "must_change_password = false, "
                        + "password_hash = '$2a$10$494KRTD7NLmmrKANwVPvQ.xFiOxOpaw9eS4p3hWmzs8GFT2ssDIri' "
                        + "where id = 5");
        for (String[] entityAction : new String[][] {{"AUTH", "LOGIN"}, {"AUTH", "LOGOUT"}, {"USER", "CHANGE_PASSWORD"}}) {
            initialAuditCounts.put(auditKey(entityAction[0], entityAction[1]), currentAuditCount(entityAction[0], entityAction[1]));
        }
    }

    @Test
    void successfulLoginReturnsCurrentUserAndSecureCookieAndActiveUseTouchesSession() throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(LOGIN_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.username").value("emp.jsmith"))
                .andExpect(jsonPath("$.role").value("EMPLOYEE"))
                .andExpect(jsonPath("$.mustChangePassword").value(false))
                .andReturn();

        String cookieHeader = result.getResponse().getHeader("Set-Cookie");
        assertThat(cookieHeader)
                .contains("HttpOnly")
                .contains("Secure")
                .contains("SameSite=Strict")
                .contains("Max-Age=900");
        String token = cookieHeader.substring("access_token=".length(), cookieHeader.indexOf(';'));
        JwtService.ParsedToken parsed = jwtService.parseAndValidate(token);
        assertThat(parsed.userId()).isEqualTo(5L);
        assertThat(parsed.role()).isEqualTo("EMPLOYEE");
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from active_sessions where token_id = ? and user_id = ?",
                Integer.class, parsed.tokenId(), 5L)).isEqualTo(1);

        jdbcTemplate.update(
                "update active_sessions set last_seen_at = now() - interval '5 minutes' where token_id = ?",
                parsed.tokenId());
        mockMvc.perform(get("/not-a-real-endpoint").cookie(new Cookie("access_token", token)))
                .andExpect(status().isNotFound());
        assertThat(jdbcTemplate.queryForObject(
                "select last_seen_at > now() - interval '1 minute' from active_sessions where token_id = ?",
                Boolean.class, parsed.tokenId())).isTrue();
        assertThat(auditCount("LOGIN")).isEqualTo(1);
    }

    @Test
    void wrongPasswordAndUnknownUsernameHaveSameUnauthorizedResponseWithoutCookie() throws Exception {
        MvcResult wrongPassword = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"emp.jsmith\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andReturn();
        MvcResult unknownUser = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"not-a-user\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andReturn();

        assertThat(wrongPassword.getResponse().getContentAsString())
                .isEqualTo(unknownUser.getResponse().getContentAsString());
        assertThat(wrongPassword.getResponse().getHeader("Set-Cookie")).isNull();
        assertThat(unknownUser.getResponse().getHeader("Set-Cookie")).isNull();
        assertThat(auditCount("LOGIN")).isZero();
    }

    @Test
    void fifthConsecutiveFailureLocksAccountAndRevokesExistingSessions() throws Exception {
        String existingToken = loginAndGetToken();
        jdbcTemplate.update("update users set failed_logins = 4, locked_until = null where id = 5");

        MvcResult fifthFailure = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"emp.jsmith\",\"password\":\"wrong\"}"))
                .andExpect(status().isLocked())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"))
                .andExpect(jsonPath("$.detail").value(
                        org.hamcrest.Matchers.containsString("after 5 failed login attempts.")))
                .andReturn();

        assertThat(fifthFailure.getResponse().getHeader("Set-Cookie")).isNull();
        assertThat(jdbcTemplate.queryForObject(
                "select failed_logins from users where id = 5", Integer.class)).isEqualTo(5);
        assertThat(jdbcTemplate.queryForObject(
                "select locked_until > now() + interval '14 minutes' "
                        + "and locked_until <= now() + interval '15 minutes' from users where id = 5",
                Boolean.class)).isTrue();
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from active_sessions where user_id = 5", Integer.class)).isZero();
        Timestamp lockedUntil = jdbcTemplate.queryForObject(
                "select locked_until from users where id = 5", Timestamp.class);

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(LOGIN_BODY))
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"))
                .andExpect(result -> assertThat(result.getResponse().getHeader("Set-Cookie")).isNull());
        assertThat(jdbcTemplate.queryForObject(
                "select locked_until from users where id = 5", Timestamp.class)).isEqualTo(lockedUntil);
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"emp.jsmith\",\"password\":\"wrong\"}"))
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"));
        assertThat(jdbcTemplate.queryForObject(
                "select locked_until from users where id = 5", Timestamp.class)).isEqualTo(lockedUntil);
        mockMvc.perform(post("/auth/logout").cookie(new Cookie("access_token", existingToken)))
                .andExpect(status().isUnauthorized());

        assertThat(auditCount("LOGIN")).isEqualTo(1);
        assertThat(auditCount("LOGOUT")).isZero();
    }

    @Test
    void failedPasswordsForInactiveAccountStillTriggerLockout() throws Exception {
        jdbcTemplate.update("update users set active = false where id = 5");

        for (int attempt = 0; attempt < 4; attempt++) {
            mockMvc.perform(post("/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"emp.jsmith\",\"password\":\"wrong\"}"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        }
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"emp.jsmith\",\"password\":\"wrong\"}"))
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"));

        assertThat(jdbcTemplate.queryForObject(
                "select failed_logins from users where id = 5", Integer.class)).isEqualTo(5);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from active_sessions where user_id = 5", Integer.class)).isZero();
    }

    @Test
    void concurrentFailuresAreSerializedWithoutLosingCounterIncrements() throws Exception {
        List<Integer> statuses = performConcurrentLogins("wrong", "wrong", "wrong", "wrong", "wrong");

        assertThat(statuses).containsExactlyInAnyOrder(401, 401, 401, 401, 423);

        assertThat(jdbcTemplate.queryForObject(
                "select failed_logins from users where id = 5", Integer.class)).isEqualTo(5);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from active_sessions where user_id = 5", Integer.class)).isZero();
    }

    @Test
    void concurrentSuccessfulLoginAndFifthFailureCannotCreateSessionAfterLockout() throws Exception {
        String existingToken = loginAndGetToken();
        jdbcTemplate.update("update users set failed_logins = 4, locked_until = null where id = 5");

        List<Integer> statuses = performConcurrentLogins("Passw0rd1", "wrong");
        if (statuses.contains(200)) {
            assertThat(statuses).containsExactlyInAnyOrder(200, 401);
            assertThat(jdbcTemplate.queryForObject(
                    "select failed_logins from users where id = 5", Integer.class)).isEqualTo(1);
            assertThat(jdbcTemplate.queryForObject(
                    "select count(*) from active_sessions where user_id = 5", Integer.class)).isEqualTo(2);
        } else {
            assertThat(statuses).containsExactlyInAnyOrder(423, 423);
            assertThat(jdbcTemplate.queryForObject(
                    "select failed_logins from users where id = 5", Integer.class)).isEqualTo(5);
            assertThat(jdbcTemplate.queryForObject(
                    "select locked_until is not null from users where id = 5", Boolean.class)).isTrue();
            assertThat(jdbcTemplate.queryForObject(
                    "select count(*) from active_sessions where user_id = 5", Integer.class)).isZero();
            mockMvc.perform(post("/auth/logout").cookie(new Cookie("access_token", existingToken)))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Test
    void successfulLoginResetsConsecutiveFailedAttempts() throws Exception {
        for (int attempt = 0; attempt < 4; attempt++) {
            mockMvc.perform(post("/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"emp.jsmith\",\"password\":\"wrong\"}"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        }
        assertThat(jdbcTemplate.queryForObject(
                "select failed_logins from users where id = 5", Integer.class)).isEqualTo(4);

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(LOGIN_BODY))
                .andExpect(status().isOk());

        assertThat(jdbcTemplate.queryForObject(
                "select failed_logins from users where id = 5", Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "select locked_until from users where id = 5", java.sql.Timestamp.class)).isNull();
    }

    @Test
    void expiredLockAllowsLoginAndResetsFailedAttempts() throws Exception {
        jdbcTemplate.update(
                "update users set failed_logins = 5, locked_until = ? where id = 5",
                java.sql.Timestamp.from(Instant.now().minusSeconds(60)));

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(LOGIN_BODY))
                .andExpect(status().isOk());

        assertThat(jdbcTemplate.queryForObject(
                "select failed_logins from users where id = 5", Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "select locked_until from users where id = 5", java.sql.Timestamp.class)).isNull();
    }

    @Test
    void missingOrBlankCredentialsReturnValidationErrorsWithoutCookie() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"emp.jsmith\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(result -> assertThat(result.getResponse().getHeader("Set-Cookie")).isNull());

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"emp.jsmith\",\"password\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(result -> assertThat(result.getResponse().getHeader("Set-Cookie")).isNull());
    }

    @Test
    void idleSessionIsRejectedWithoutUpdatingSessionOrWritingAnAuditEvent() throws Exception {
        String token = loginAndGetToken();
        String tokenId = jwtService.parseAndValidate(token).tokenId();
        jdbcTemplate.update(
                "update active_sessions set last_seen_at = now() - interval '31 minutes' where token_id = ?",
                tokenId);

        mockMvc.perform(post("/auth/logout").cookie(new Cookie("access_token", token)))
                .andExpect(status().isUnauthorized());

        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from active_sessions where token_id = ?", Integer.class, tokenId)).isEqualTo(1);
        assertThat(auditCount("LOGIN")).isEqualTo(1);
        assertThat(auditCount("LOGOUT")).isZero();
    }

    @Test
    void emptyAccessTokenCookieIsRejectedAsUnauthenticated() throws Exception {
        mockMvc.perform(post("/auth/logout").cookie(new Cookie("access_token", "")))
                .andExpect(status().isUnauthorized());

        assertThat(auditCount("LOGOUT")).isZero();
    }

    @Test
    void logoutRevokesAllUserSessionsClearsCookieAndRejectsOldTokens() throws Exception {
        String firstToken = loginAndGetToken();
        String secondToken = loginAndGetToken();

        MvcResult logout = mockMvc.perform(post("/auth/logout")
                        .cookie(new Cookie("access_token", firstToken)))
                .andExpect(status().isNoContent())
                .andReturn();

        assertThat(logout.getResponse().getHeader("Set-Cookie"))
                .contains("access_token=")
                .contains("HttpOnly")
                .contains("Secure")
                .contains("SameSite=Strict")
                .contains("Max-Age=0");
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from active_sessions where user_id = 5", Integer.class)).isZero();
        mockMvc.perform(post("/auth/logout").cookie(new Cookie("access_token", secondToken)))
                .andExpect(status().isUnauthorized());
        assertThat(auditCount("LOGIN")).isEqualTo(2);
        assertThat(auditCount("LOGOUT")).isEqualTo(1);
        assertThat(auditCount("LOGIN") + auditCount("LOGOUT")).isEqualTo(3);
    }

    @Test
    void successfulChangePasswordReplacesHashClearsMustChangeFlagAndWritesAudit() throws Exception {
        jdbcTemplate.update("update users set must_change_password = true where id = 5");
        String token = loginAndGetToken();
        String originalHash = jdbcTemplate.queryForObject(
                "select password_hash from users where id = 5", String.class);

        mockMvc.perform(post("/auth/change-password")
                        .cookie(new Cookie("access_token", token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"Passw0rd1\",\"newPassword\":\"NewPass2\"}"))
                .andExpect(status().isNoContent());

        assertThat(jdbcTemplate.queryForObject(
                "select must_change_password from users where id = 5", Boolean.class)).isFalse();
        assertThat(jdbcTemplate.queryForObject(
                "select password_hash from users where id = 5", String.class)).isNotEqualTo(originalHash);
        assertThat(auditCount("USER", "CHANGE_PASSWORD")).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                        "select count(*) from audit_events where entity = 'USER' and action = 'CHANGE_PASSWORD' "
                                + "and entity_id = 5 and actor_id = 5 and created_at is not null",
                        Integer.class))
                .isEqualTo(1);

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"emp.jsmith\",\"password\":\"NewPass2\"}"))
                .andExpect(status().isOk());

        // Self-service changePassword must never call sessionRegistry.revokeAllForUser: the
        // original session cookie is still valid afterwards, not rejected as unauthorized.
        mockMvc.perform(post("/auth/logout").cookie(new Cookie("access_token", token)))
                .andExpect(status().isNoContent());
    }

    @Test
    void blankCurrentOrNewPasswordIsRejectedWithValidationFailed() throws Exception {
        String token = loginAndGetToken();

        mockMvc.perform(post("/auth/change-password")
                        .cookie(new Cookie("access_token", token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"\",\"newPassword\":\"NewPass2\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        mockMvc.perform(post("/auth/change-password")
                        .cookie(new Cookie("access_token", token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"Passw0rd1\",\"newPassword\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        assertThat(auditCount("USER", "CHANGE_PASSWORD")).isZero();
    }

    @Test
    void weakNewPasswordIsRejectedWithValidationFailedAndHashUnchanged() throws Exception {
        String token = loginAndGetToken();
        String originalHash = jdbcTemplate.queryForObject(
                "select password_hash from users where id = 5", String.class);

        for (String weakPassword : List.of("short1", "alllettersnodigit", "12345678")) {
            mockMvc.perform(post("/auth/change-password")
                            .cookie(new Cookie("access_token", token))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"currentPassword\":\"Passw0rd1\",\"newPassword\":\"" + weakPassword + "\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        }

        assertThat(jdbcTemplate.queryForObject(
                "select password_hash from users where id = 5", String.class)).isEqualTo(originalHash);
        assertThat(auditCount("USER", "CHANGE_PASSWORD")).isZero();
    }

    @Test
    void wrongCurrentPasswordIsRejectedWithInvalidCredentialsAndHashUnchanged() throws Exception {
        String token = loginAndGetToken();
        String originalHash = jdbcTemplate.queryForObject(
                "select password_hash from users where id = 5", String.class);

        mockMvc.perform(post("/auth/change-password")
                        .cookie(new Cookie("access_token", token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"WrongPass1\",\"newPassword\":\"NewPass2\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));

        assertThat(jdbcTemplate.queryForObject(
                "select password_hash from users where id = 5", String.class)).isEqualTo(originalHash);
        assertThat(auditCount("USER", "CHANGE_PASSWORD")).isZero();
    }

    private String loginAndGetToken() throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(LOGIN_BODY))
                .andExpect(status().isOk())
                .andReturn();
        String cookieHeader = result.getResponse().getHeader("Set-Cookie");
        return cookieHeader.substring("access_token=".length(), cookieHeader.indexOf(';'));
    }

    private List<Integer> performConcurrentLogins(String... passwords) throws Exception {
        CountDownLatch ready = new CountDownLatch(passwords.length);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(passwords.length);
        try {
            List<Future<Integer>> attempts = new ArrayList<>();
            for (String password : passwords) {
                attempts.add(executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Timed out waiting to start concurrent login attempts.");
                    }
                    return mockMvc.perform(post("/auth/login")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("{\"username\":\"emp.jsmith\",\"password\":\""
                                            + password + "\"}"))
                            .andReturn()
                            .getResponse()
                            .getStatus();
                }));
            }

            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> attempt : attempts) {
                statuses.add(attempt.get(30, TimeUnit.SECONDS));
            }
            return statuses;
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    private int auditCount(String action) {
        return auditCount("AUTH", action);
    }

    private int auditCount(String entity, String action) {
        return currentAuditCount(entity, action) - initialAuditCounts.get(auditKey(entity, action));
    }

    private int currentAuditCount(String action) {
        return currentAuditCount("AUTH", action);
    }

    private int currentAuditCount(String entity, String action) {
        return jdbcTemplate.queryForObject(
                "select count(*) from audit_events where entity = ? and action = ? "
                        + "and actor_id = 5 and created_at is not null",
                Integer.class, entity, action);
    }

    private String auditKey(String entity, String action) {
        return entity + ":" + action;
    }
}
