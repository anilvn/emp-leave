package com.company.leave;

import java.util.List;
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

/**
 * Context-load test proving the backend starts against the database and that Flyway
 * creates exactly its own bookkeeping table plus the two tables Story 1.2 adds —
 * {@code audit_events} and {@code access_log}; no other domain table exists yet.
 */
@SpringBootTest
@Testcontainers
class CompanyLeaveApplicationTests {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18");

    @Autowired
    private DataSource dataSource;

    @Test
    void contextLoadsAndFlywayCreatesHistoryAndAuditTables() {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        List<String> tables = jdbcTemplate.queryForList(
                "select table_name from information_schema.tables where table_schema = 'public'",
                String.class);

        assertThat(tables).containsExactlyInAnyOrder("flyway_schema_history", "audit_events", "access_log");
    }
}
