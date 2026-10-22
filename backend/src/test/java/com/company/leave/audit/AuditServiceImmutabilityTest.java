package com.company.leave.audit;

import javax.sql.DataSource;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * Proves both halves of FR44/AD-6: {@link AuditService} can write to {@code audit_events}
 * and {@code access_log}, and nothing — not even a raw {@link JdbcTemplate} statement
 * bypassing the service entirely, which stands in for "someone with raw DB access" — can
 * update or delete a row afterwards (NFR10).
 */
@SpringBootTest
@Testcontainers
class AuditServiceImmutabilityTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18");

    @Autowired
    private AuditService auditService;

    @Autowired
    private DataSource dataSource;

    private JdbcTemplate jdbcTemplate;

    private JdbcTemplate jdbcTemplate() {
        if (jdbcTemplate == null) {
            jdbcTemplate = new JdbcTemplate(dataSource);
        }
        return jdbcTemplate;
    }

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void recordEventInsertsARowThatCannotLaterBeUpdatedOrDeleted() throws Exception {
        auditService.recordEvent("LEAVE_REQUEST", 42L, "APPROVE", 7L, "{\"note\":\"ok\"}");

        Long id = jdbcTemplate().queryForObject(
                "select id from audit_events where entity = 'LEAVE_REQUEST' and entity_id = 42", Long.class);
        assertThat(id).isNotNull();

        assertThatExceptionOfType(DataAccessException.class)
                .isThrownBy(() -> jdbcTemplate().update("update audit_events set action = 'TAMPERED' where id = ?", id));

        assertThatExceptionOfType(DataAccessException.class)
                .isThrownBy(() -> jdbcTemplate().update("delete from audit_events where id = ?", id));

        Long stillThere = jdbcTemplate().queryForObject(
                "select count(*) from audit_events where id = ? and action = 'APPROVE'", Long.class, id);
        assertThat(stillThere).isEqualTo(1L);

        String storedJson = jdbcTemplate().queryForObject(
                "select details_json from audit_events where id = ?", String.class, id);
        assertThat(objectMapper.readTree(storedJson)).isEqualTo(objectMapper.readTree("{\"note\":\"ok\"}"));
    }

    @Test
    void auditEventsActorIdRejectsNull() {
        assertThatExceptionOfType(DataAccessException.class)
                .isThrownBy(() -> jdbcTemplate().update(
                        "insert into audit_events (entity, entity_id, action, actor_id, created_at) "
                                + "values ('LEAVE_REQUEST', 1, 'APPROVE', null, now())"));
    }

    @Test
    void recordAccessAcceptsNullActorIdAndResourceId() {
        auditService.recordAccess(null, "LEAVE_REQUEST", null, true);

        Long count = jdbcTemplate().queryForObject(
                "select count(*) from access_log where resource = 'LEAVE_REQUEST' "
                        + "and actor_id is null and resource_id is null and allowed = true",
                Long.class);
        assertThat(count).isEqualTo(1L);
    }

    @Test
    void recordAccessInsertsARowThatCannotLaterBeUpdatedOrDeleted() {
        auditService.recordAccess(7L, "LEAVE_REQUEST", 42L, false);

        Long id = jdbcTemplate().queryForObject(
                "select id from access_log where resource = 'LEAVE_REQUEST' and resource_id = 42", Long.class);
        assertThat(id).isNotNull();

        assertThatExceptionOfType(DataAccessException.class)
                .isThrownBy(() -> jdbcTemplate().update("update access_log set allowed = true where id = ?", id));

        assertThatExceptionOfType(DataAccessException.class)
                .isThrownBy(() -> jdbcTemplate().update("delete from access_log where id = ?", id));

        Long stillThere = jdbcTemplate().queryForObject(
                "select count(*) from access_log where id = ? and allowed = false", Long.class, id);
        assertThat(stillThere).isEqualTo(1L);
    }
}
