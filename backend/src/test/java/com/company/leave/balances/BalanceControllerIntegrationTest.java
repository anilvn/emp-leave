package com.company.leave.balances;

import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import com.jayway.jsonpath.JsonPath;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@Testcontainers
@SpringBootTest
class BalanceControllerIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DataSource dataSource;

    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.update("delete from active_sessions");
        jdbcTemplate.update("update users set must_change_password = false where id = 5");
    }

    @AfterEach
    void restoreSeededRowsAndAuthState() {
        jdbcTemplate.update("update users set must_change_password = false where id = 5");
        jdbcTemplate.update("update leave_balances set reserved_days = 0");
        jdbcTemplate.update("""
                insert into leave_balances (user_id, leave_type_id, total_days, reserved_days)
                select users.id,
                       leave_types.id,
                       case leave_types.name
                           when 'Vacation' then 20
                           when 'Sick' then 10
                           when 'Parental' then 30
                           when 'Sabbatical' then 0
                       end,
                       0
                from users
                cross join leave_types
                on conflict (user_id, leave_type_id) do update
                    set total_days = excluded.total_days,
                        reserved_days = 0
                """);
    }

    @ParameterizedTest
    @CsvSource({"5, EMPLOYEE", "3, MANAGER", "1, HR_ADMIN"})
    void everySupportedRoleGetsEveryTypeWithTotalsAndComputedAvailability(long userId, String role)
            throws Exception {
        MvcResult result = mockMvc.perform(get("/balances/me").with(authenticatedAs(userId, role)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[*].userId").value(Matchers.everyItem(Matchers.is((int) userId))))
                .andReturn();

        List<Map<String, Object>> rows = balanceRows(result);
        List<Long> returnedTypeIds = rows.stream()
                .map(row -> number(row, "leaveTypeId").longValue())
                .toList();
        List<Long> seededTypeIds = jdbcTemplate.queryForList(
                "select id from leave_types order by id", Long.class);
        assertThat(returnedTypeIds).containsExactlyElementsOf(seededTypeIds);
        assertThat(rows).extracting(row -> number(row, "totalDays").intValue())
                .containsExactlyInAnyOrder(20, 10, 30, 0);

        Map<Long, Integer> expectedDaysByType = Map.of(
                leaveTypeId("Vacation"), 20,
                leaveTypeId("Sick"), 10,
                leaveTypeId("Parental"), 30,
                leaveTypeId("Sabbatical"), 0);
        for (Map<String, Object> row : rows) {
            assertThat(row).containsKeys("userId", "leaveTypeId", "totalDays", "reservedDays", "availableDays");
            long typeId = number(row, "leaveTypeId").longValue();
            int totalDays = number(row, "totalDays").intValue();
            int reservedDays = number(row, "reservedDays").intValue();
            assertThat(totalDays).isEqualTo(expectedDaysByType.get(typeId));
            assertThat(reservedDays).isZero();
            assertThat(number(row, "availableDays").intValue()).isEqualTo(expectedDaysByType.get(typeId));
        }
    }

    @Test
    void returnsTheRequestedUsersBalanceRows() throws Exception {
        long vacationTypeId = leaveTypeId("Vacation");
        jdbcTemplate.update(
                "update leave_balances set total_days = 17, reserved_days = 2 "
                        + "where user_id = 3 and leave_type_id = ?",
                vacationTypeId);

        MvcResult managerResult = mockMvc.perform(get("/balances/me").with(authenticatedAs(3L, "MANAGER")))
                .andExpect(status().isOk())
                .andReturn();
        Map<String, Object> managerVacation = balanceForType(managerResult, vacationTypeId);
        assertThat(number(managerVacation, "userId").longValue()).isEqualTo(3L);
        assertThat(number(managerVacation, "totalDays").intValue()).isEqualTo(17);
        assertThat(number(managerVacation, "reservedDays").intValue()).isEqualTo(2);
        assertThat(number(managerVacation, "availableDays").intValue()).isEqualTo(15);

        MvcResult employeeResult = mockMvc.perform(get("/balances/me").with(authenticatedAs(5L, "EMPLOYEE")))
                .andExpect(status().isOk())
                .andReturn();
        Map<String, Object> employeeVacation = balanceForType(employeeResult, vacationTypeId);
        assertThat(number(employeeVacation, "userId").longValue()).isEqualTo(5L);
        assertThat(number(employeeVacation, "totalDays").intValue()).isEqualTo(20);
        assertThat(number(employeeVacation, "reservedDays").intValue()).isZero();
        assertThat(number(employeeVacation, "availableDays").intValue()).isEqualTo(20);
    }

    @Test
    void availableDaysSubtractsReservations() throws Exception {
        long vacationTypeId = leaveTypeId("Vacation");
        jdbcTemplate.update(
                "update leave_balances set reserved_days = 4 where user_id = 5 and leave_type_id = ?",
                vacationTypeId);

        MvcResult result = mockMvc.perform(get("/balances/me").with(authenticatedAs(5L, "EMPLOYEE")))
                .andExpect(status().isOk())
                .andReturn();

        Map<String, Object> vacation = balanceForType(result, vacationTypeId);
        assertThat(number(vacation, "totalDays").intValue()).isEqualTo(20);
        assertThat(number(vacation, "reservedDays").intValue()).isEqualTo(4);
        assertThat(number(vacation, "availableDays").intValue()).isEqualTo(16);
    }

    @Test
    void missingBalanceRowAppearsWithZeroDays() throws Exception {
        long vacationTypeId = leaveTypeId("Vacation");
        assertThat(jdbcTemplate.update(
                        "delete from leave_balances where user_id = 5 and leave_type_id = ?", vacationTypeId))
                .isEqualTo(1);

        MvcResult result = mockMvc.perform(get("/balances/me").with(authenticatedAs(5L, "EMPLOYEE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                .andReturn();

        Map<String, Object> vacation = balanceForType(result, vacationTypeId);
        assertThat(number(vacation, "totalDays").intValue()).isZero();
        assertThat(number(vacation, "reservedDays").intValue()).isZero();
        assertThat(number(vacation, "availableDays").intValue()).isZero();
    }

    @Test
    void seededTypesAndAllFortyEightUserTypeBalancesHaveContractualAllocations() {
        assertThat(jdbcTemplate.queryForList("select name from leave_types order by name", String.class))
                .containsExactly("Parental", "Sabbatical", "Sick", "Vacation");
        assertThat(jdbcTemplate.queryForObject("select count(*) from users", Integer.class)).isEqualTo(12);
        assertThat(jdbcTemplate.queryForObject("select count(*) from leave_balances", Integer.class)).isEqualTo(48);
        assertThat(jdbcTemplate.queryForObject("""
                        select count(*) from (
                            select user_id from leave_balances group by user_id having count(*) = 4
                        ) per_user
                        """, Integer.class))
                .isEqualTo(12);

        Map<String, Integer> expectedDays = Map.of(
                "Vacation", 20,
                "Sick", 10,
                "Parental", 30,
                "Sabbatical", 0);
        for (Map.Entry<String, Integer> expected : expectedDays.entrySet()) {
            assertThat(jdbcTemplate.queryForObject("""
                            select count(*) from leave_balances balances
                            join leave_types types on types.id = balances.leave_type_id
                            where types.name = ? and balances.total_days = ? and balances.reserved_days = 0
                            """, Integer.class, expected.getKey(), expected.getValue()))
                    .isEqualTo(12);
        }
    }

    @Test
    void checkConstraintRejectsNegativeDayCountsAndAvailableDays() {
        long vacationTypeId = leaveTypeId("Vacation");

        assertThatThrownBy(() -> jdbcTemplate.update(
                        "update leave_balances set total_days = -1, reserved_days = -2 "
                                + "where user_id = 5 and leave_type_id = ?",
                        vacationTypeId))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbcTemplate.update(
                        "update leave_balances set reserved_days = -1 where user_id = 5 and leave_type_id = ?",
                        vacationTypeId))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbcTemplate.update(
                        "update leave_balances set reserved_days = 21 where user_id = 5 and leave_type_id = ?",
                        vacationTypeId))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(jdbcTemplate.queryForObject(
                        "select total_days - reserved_days from leave_balances "
                                + "where user_id = 5 and leave_type_id = ?",
                        Integer.class,
                        vacationTypeId))
                .isEqualTo(20);
    }

    @Test
    void unauthenticatedCallerGets401() throws Exception {
        mockMvc.perform(get("/balances/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void callerWhoMustChangePasswordGets403() throws Exception {
        jdbcTemplate.update("update users set must_change_password = true where id = 5");

        mockMvc.perform(get("/balances/me").with(authenticatedAs(5L, "EMPLOYEE")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_REQUIRED"));
    }

    // --- Story 4.1: GET /balances (HR only) ---

    @Test
    void hrAdminGetsAllBalancesForAllUsers() throws Exception {
        MvcResult result = mockMvc.perform(get("/balances").with(authenticatedAs(1L, "HR_ADMIN")))
                .andExpect(status().isOk())
                .andReturn();

        List<Map<String, Object>> rows = balanceRows(result);
        assertThat(rows).isNotEmpty();
        int userCount = jdbcTemplate.queryForObject("select count(*) from users", Integer.class);
        int typeCount = jdbcTemplate.queryForObject("select count(*) from leave_types", Integer.class);
        assertThat(rows).hasSize(userCount * typeCount);
        assertThat(rows).allSatisfy(row ->
                assertThat(row).containsKeys("userId", "leaveTypeId", "totalDays", "reservedDays", "availableDays"));
    }

    @Test
    void hrAdminGetsBalancesFilteredByUserId() throws Exception {
        MvcResult result = mockMvc.perform(get("/balances?userId=5").with(authenticatedAs(1L, "HR_ADMIN")))
                .andExpect(status().isOk())
                .andReturn();

        List<Map<String, Object>> rows = balanceRows(result);
        assertThat(rows).isNotEmpty();
        assertThat(rows).allSatisfy(row ->
                assertThat(number(row, "userId").longValue()).isEqualTo(5L));
    }

    @Test
    void employeeIsRefusedFromGetAllBalances() throws Exception {
        mockMvc.perform(get("/balances").with(authenticatedAs(5L, "EMPLOYEE")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void managerIsRefusedFromGetAllBalances() throws Exception {
        mockMvc.perform(get("/balances").with(authenticatedAs(3L, "MANAGER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void unauthenticatedCallerGets401ForAllBalances() throws Exception {
        mockMvc.perform(get("/balances")).andExpect(status().isUnauthorized());
    }

    private long leaveTypeId(String name) {
        return jdbcTemplate.queryForObject("select id from leave_types where name = ?", Long.class, name);
    }

    private List<Map<String, Object>> balanceRows(MvcResult result) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), "$");
    }

    private Map<String, Object> balanceForType(MvcResult result, long leaveTypeId) throws Exception {
        return balanceRows(result).stream()
                .filter(row -> number(row, "leaveTypeId").longValue() == leaveTypeId)
                .findFirst()
                .orElseThrow();
    }

    private Number number(Map<String, Object> row, String field) {
        return (Number) row.get(field);
    }

    private RequestPostProcessor authenticatedAs(long userId, String role) {
        return authentication(new TestingAuthenticationToken(
                userId, null, new SimpleGrantedAuthority("ROLE_" + role)));
    }
}
