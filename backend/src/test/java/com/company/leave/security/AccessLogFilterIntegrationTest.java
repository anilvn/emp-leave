package com.company.leave.security;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
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
@Import(AccessLogFilterIntegrationTest.TestController.class)
class AccessLogFilterIntegrationTest {

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
    }

    @Test
    void authenticatedApiRequestWritesExactlyOneAllowedRowWithPathId() throws Exception {
        long actorId = 7001L;
        long resourceId = System.currentTimeMillis();
        String resource = "GET /api/audit-test/" + resourceId;

        mockMvc.perform(get("/audit-test/{id}", resourceId)
                        .with(forwardedApiPrefix())
                        .with(authenticatedAs(actorId)))
                .andExpect(status().isOk());

        assertThat(countRows(resource)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "select actor_id from access_log where resource = ?", Long.class, resource)).isEqualTo(actorId);
        assertThat(jdbcTemplate.queryForObject(
                "select resource_id from access_log where resource = ?", Long.class, resource)).isEqualTo(resourceId);
        assertThat(jdbcTemplate.queryForObject(
                "select allowed from access_log where resource = ?", Boolean.class, resource)).isTrue();
    }

    @Test
    void authenticatedDeniedApiRequestWritesOneDeniedRow() throws Exception {
        long actorId = 7002L;
        long resourceId = System.currentTimeMillis();
        String resource = "GET /api/audit-test/denied/" + resourceId;

        mockMvc.perform(get("/audit-test/denied/{id}", resourceId)
                        .with(forwardedApiPrefix())
                        .with(authenticatedAs(actorId)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        assertThat(countRows(resource)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "select actor_id from access_log where resource = ?", Long.class, resource)).isEqualTo(actorId);
        assertThat(jdbcTemplate.queryForObject(
                "select resource_id from access_log where resource = ?", Long.class, resource)).isEqualTo(resourceId);
        assertThat(jdbcTemplate.queryForObject(
                "select allowed from access_log where resource = ?", Boolean.class, resource)).isFalse();
    }

    @Test
    void unauthenticatedAndPublicRequestsAreOmitted() throws Exception {
        long resourceId = System.currentTimeMillis();
        String resource = "GET /api/audit-test/" + resourceId;

        mockMvc.perform(get("/audit-test/{id}", resourceId).with(forwardedApiPrefix()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/actuator/health").with(authenticatedAs(7003L)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/auth/login")
                        .with(forwardedApiPrefix())
                        .with(authenticatedAs(7004L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"missing-user\",\"password\":\"NotReal123\"}"))
                .andExpect(status().isUnauthorized());

        assertThat(countRows(resource)).isZero();
        assertThat(countRows("GET /actuator/health")).isZero();
        assertThat(countRows("POST /api/auth/login")).isZero();
    }

    private RequestPostProcessor forwardedApiPrefix() {
        return request -> {
            request.addHeader("X-Forwarded-Prefix", "/api");
            return request;
        };
    }

    private RequestPostProcessor authenticatedAs(long actorId) {
        return authentication(new TestingAuthenticationToken(
                actorId, null, new SimpleGrantedAuthority("ROLE_EMPLOYEE")));
    }

    private int countRows(String resource) {
        return jdbcTemplate.queryForObject(
                "select count(*) from access_log where resource = ?", Integer.class, resource);
    }

    @RestController
    @RequestMapping("/audit-test")
    static class TestController {

        @GetMapping("/{id}")
        ResponseEntity<Void> allowed(@PathVariable long id) {
            return ResponseEntity.ok().build();
        }

        @GetMapping("/denied/{id}")
        ResponseEntity<Void> denied(@PathVariable long id) {
            throw new AccessDeniedException("denied for test");
        }
    }
}
