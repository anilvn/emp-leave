package com.company.leave.security;

import java.time.Instant;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class SessionRegistryTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18");

    @Autowired
    private SessionRegistry sessionRegistry;

    @Autowired
    private DataSource dataSource;

    @Test
    void createsTouchesRejectsIdleSessionsAndRevokesEverySessionForAUser() {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        long userId = 1L;

        String tokenId = sessionRegistry.createSession(userId);
        Instant createdLastSeen = jdbcTemplate.queryForObject(
                "select last_seen_at from active_sessions where token_id = ?", Instant.class, tokenId);
        assertThat(sessionRegistry.touchOrReject(tokenId)).isTrue();
        Instant touchedLastSeen = jdbcTemplate.queryForObject(
                "select last_seen_at from active_sessions where token_id = ?", Instant.class, tokenId);
        assertThat(touchedLastSeen).isAfterOrEqualTo(createdLastSeen);

        jdbcTemplate.update(
                "update active_sessions set last_seen_at = now() - interval '31 minutes' where token_id = ?",
                tokenId);
        assertThat(sessionRegistry.touchOrReject(tokenId)).isFalse();

        jdbcTemplate.update(
                "update active_sessions set expires_at = now() - interval '1 minute' where token_id = ?",
                tokenId);
        String anotherTokenId = sessionRegistry.createSession(userId);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from active_sessions where token_id = ?", Integer.class, tokenId)).isZero();
        String secondTokenId = sessionRegistry.createSession(userId);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from active_sessions where user_id = ?", Integer.class, userId)).isEqualTo(2);
        sessionRegistry.revokeAllForUser(userId);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from active_sessions where user_id = ?", Integer.class, userId)).isZero();
        assertThat(sessionRegistry.touchOrReject(anotherTokenId)).isFalse();
        assertThat(sessionRegistry.touchOrReject(secondTokenId)).isFalse();
    }
}
