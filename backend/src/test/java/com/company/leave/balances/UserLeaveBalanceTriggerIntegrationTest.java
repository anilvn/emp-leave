package com.company.leave.balances;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.configuration.FluentConfiguration;
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
class UserLeaveBalanceTriggerIntegrationTest {

    private static final Map<String, Balance> DEFAULT_BALANCES = Map.of(
            "Vacation", new Balance(20, 0),
            "Sick", new Balance(10, 0),
            "Parental", new Balance(10, 0),
            "Sabbatical", new Balance(5, 0));

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18");

    @Autowired
    private DataSource dataSource;

    @Test
    void directUserInsertsCreateDefaultBalancesForEveryRole() {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        for (String role : List.of("EMPLOYEE", "MANAGER", "HR_ADMIN")) {
            String username = "balance-trigger-" + role.toLowerCase() + "-" + UUID.randomUUID();
            jdbcTemplate.update(
                    "INSERT INTO users (username, password_hash, role) VALUES (?, ?, ?)",
                    username, "integration-test-password-hash", role);
            Long userId = jdbcTemplate.queryForObject(
                    "SELECT id FROM users WHERE username = ?", Long.class, username);

            assertThat(userId).isNotNull();
            assertBalances(jdbcTemplate, "public", userId, DEFAULT_BALANCES);
        }
    }

    @Test
    void migrationBackfillsMissingBalancesWithoutChangingExistingValues() {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        String schema = "balance_backfill_" + UUID.randomUUID().toString().replace("-", "");

        try {
            configureFlyway(schema).target(MigrationVersion.fromVersion("13")).load().migrate();

            jdbcTemplate.update("""
                    UPDATE %s.leave_balances
                    SET total_days = 23, reserved_days = 2
                    WHERE user_id = 1
                      AND leave_type_id = (SELECT id FROM %s.leave_types WHERE name = 'Vacation')
                    """.formatted(schema, schema));
            jdbcTemplate.update("""
                    DELETE FROM %s.leave_balances
                    WHERE user_id = 1
                      AND leave_type_id IN (
                          SELECT id FROM %s.leave_types
                          WHERE name IN ('Sick', 'Parental', 'Sabbatical'))
                    """.formatted(schema, schema));
            jdbcTemplate.update(
                    "INSERT INTO " + schema
                            + ".users (username, password_hash, role) VALUES (?, ?, ?)",
                    "balance-backfill-" + UUID.randomUUID(),
                    "integration-test-password-hash",
                    "EMPLOYEE");
            Long userWithoutBalances = jdbcTemplate.queryForObject(
                    "SELECT id FROM " + schema + ".users WHERE username LIKE 'balance-backfill-%'",
                    Long.class);

            configureFlyway(schema).load().migrate();

            assertBalances(jdbcTemplate, schema, 1L, Map.of(
                    "Vacation", new Balance(23, 2),
                    "Sick", new Balance(10, 0),
                    "Parental", new Balance(10, 0),
                    "Sabbatical", new Balance(5, 0)));
            assertThat(userWithoutBalances).isNotNull();
            assertBalances(jdbcTemplate, schema, userWithoutBalances, DEFAULT_BALANCES);
        } finally {
            jdbcTemplate.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
        }
    }

    private FluentConfiguration configureFlyway(String schema) {
        return Flyway.configure()
                .dataSource(dataSource)
                .schemas(schema)
                .defaultSchema(schema)
                .locations("classpath:db/migration");
    }

    private void assertBalances(
            JdbcTemplate jdbcTemplate, String schema, long userId, Map<String, Balance> expected) {
        Map<String, Balance> actual = new HashMap<>();
        jdbcTemplate.queryForList("""
                SELECT leave_types.name, leave_balances.total_days, leave_balances.reserved_days
                FROM %s.leave_balances
                JOIN %s.leave_types ON leave_types.id = leave_balances.leave_type_id
                WHERE leave_balances.user_id = ?
                """.formatted(schema, schema), userId)
                .forEach(row -> actual.put(
                        (String) row.get("name"),
                        new Balance(
                                ((Number) row.get("total_days")).intValue(),
                                ((Number) row.get("reserved_days")).intValue())));

        assertThat(actual).isEqualTo(expected);
    }

    private record Balance(int totalDays, int reservedDays) {
    }
}
