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
 * creates its bookkeeping table and all domain tables through Epic 3 (Stories 1.2, 1.3,
 * 2.1–2.3, and 3.5 which added request_comments).
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

        assertThat(tables).containsExactlyInAnyOrder(
                "flyway_schema_history", "audit_events", "access_log", "users", "active_sessions",
                "leave_types", "leave_balances", "leave_requests", "balance_ledger", "notifications",
                "request_comments");
        assertThat(jdbcTemplate.queryForObject("select count(*) from users", Integer.class)).isEqualTo(12);
    }
}
