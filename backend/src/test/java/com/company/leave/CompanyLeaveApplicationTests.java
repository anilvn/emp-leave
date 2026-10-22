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
 * Context-load test proving the backend starts against an empty database and that
 * Flyway creates only its own {@code flyway_schema_history} bookkeeping table — this
 * story adds no domain table.
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
    void contextLoadsAndFlywayCreatesOnlyItsOwnHistoryTable() {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        List<String> tables = jdbcTemplate.queryForList(
                "select table_name from information_schema.tables where table_schema = 'public'",
                String.class);

        assertThat(tables).containsExactly("flyway_schema_history");
    }
}
