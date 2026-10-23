package com.company.leave.common;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the springdoc-generated OpenAPI document: it is reachable without authentication,
 * carries the Epic 1 endpoints, declares the {@code cookieAuth} scheme, and marks login public.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class OpenApiDocsIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18");

    @Autowired
    private MockMvc mockMvc;

    @Test
    void apiDocsAreReachableUnauthenticatedAndDescribeEpic1Endpoints() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openapi").exists())
                .andExpect(jsonPath("$.paths['/auth/login'].post").exists())
                .andExpect(jsonPath("$.paths['/auth/logout'].post").exists())
                .andExpect(jsonPath("$.paths['/auth/change-password'].post").exists())
                .andExpect(jsonPath("$.paths['/users'].post").exists())
                .andExpect(jsonPath("$.paths['/users'].get").exists())
                .andExpect(jsonPath("$.paths['/users/managers'].get").exists())
                .andExpect(jsonPath("$.paths['/requests'].get").exists())
                .andExpect(jsonPath("$.paths['/requests/{requestId}'].get").exists())
                .andExpect(jsonPath("$.paths['/users/{userId}/reset-password'].post").exists());
    }

    @Test
    void apiDocsDeclareCookieAuthSchemeAndMarkLoginPublic() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.securitySchemes.cookieAuth.type").value("apiKey"))
                .andExpect(jsonPath("$.components.securitySchemes.cookieAuth.in").value("cookie"))
                .andExpect(jsonPath("$.components.securitySchemes.cookieAuth.name").value("access_token"))
                .andExpect(jsonPath("$.paths['/auth/login'].post.security.length()").value(0));
    }

    @Test
    void swaggerUiIsReachableUnauthenticated() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
    }
}
