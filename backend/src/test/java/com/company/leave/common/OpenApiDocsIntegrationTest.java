package com.company.leave.common;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.yaml.snakeyaml.Yaml;

import static org.assertj.core.api.Assertions.assertThat;
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
                .andExpect(jsonPath("$.paths['/requests/{requestId}/history'].get").exists())
                .andExpect(jsonPath("$.paths['/requests/{requestId}/history'].get.responses['200']").exists())
                .andExpect(jsonPath("$.paths['/requests/{requestId}/history'].get.responses['401']").exists())
                .andExpect(jsonPath("$.paths['/requests/{requestId}/history'].get.responses['403']").exists())
                .andExpect(jsonPath("$.paths['/requests/{requestId}/history'].get.responses['404']").exists())
                .andExpect(jsonPath("$.paths['/requests/{requestId}'].put").exists())
                .andExpect(jsonPath("$.paths['/requests/{requestId}'].put.responses['200']").exists())
                .andExpect(jsonPath("$.paths['/requests/{requestId}'].put.responses['404']").exists())
                .andExpect(jsonPath("$.paths['/requests/{requestId}'].put.responses['400']").exists())
                .andExpect(jsonPath("$.paths['/requests/{requestId}'].put.responses['403']").exists())
                .andExpect(jsonPath("$.paths['/requests/{requestId}'].put.responses['409']").exists())
                .andExpect(jsonPath("$.paths['/requests/{requestId}'].put.requestBody.content['application/json'].schema.$ref")
                        .value("#/components/schemas/LeaveRequestUpdate"))
                .andExpect(jsonPath("$.paths['/requests/{requestId}'].put.responses['200'].content['application/json'].schema.$ref")
                        .value("#/components/schemas/LeaveRequest"))
                .andExpect(jsonPath("$.paths['/requests/{requestId}/cancel'].post").exists())
                .andExpect(jsonPath("$.paths['/requests/{requestId}/cancel'].post.responses['200']").exists())
                .andExpect(jsonPath("$.paths['/requests/{requestId}/cancel'].post.responses['401']").exists())
                .andExpect(jsonPath("$.paths['/requests/{requestId}/cancel'].post.responses['403']").exists())
                .andExpect(jsonPath("$.paths['/requests/{requestId}/cancel'].post.responses['404']").exists())
                .andExpect(jsonPath("$.paths['/requests/{requestId}/cancel'].post.responses['409']").exists())
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
    void handWrittenContractDocumentsPendingRequestUpdateOperation() throws Exception {
        try (InputStream input = getClass().getResourceAsStream("/openapi.yaml")) {
            assertThat(input).isNotNull();
            Map<?, ?> document = new Yaml().load(input);
            Map<?, ?> paths = (Map<?, ?>) document.get("paths");
            Map<?, ?> requestPath = (Map<?, ?>) paths.get("/requests/{requestId}");
            Map<?, ?> updateOperation = (Map<?, ?>) requestPath.get("put");
            Map<?, ?> responses = (Map<?, ?>) updateOperation.get("responses");

            assertThat(responses.keySet().stream().map(Object::toString).toList())
                    .contains("200", "400", "403", "404", "409");
            assertThat(schemaRef(mapAt(updateOperation, "requestBody"), "content", "application/json", "schema"))
                    .isEqualTo("#/components/schemas/LeaveRequestUpdate");
            assertThat(schemaRef(mapAt(responses, "200"), "content", "application/json", "schema"))
                    .isEqualTo("#/components/schemas/LeaveRequest");
            assertThat(problemCode(mapAt(responses, "400"), "content", "application/problem+json",
                    "examples", "validationFailed", "value"))
                    .isEqualTo("VALIDATION_FAILED");
            assertThat(problemCode(mapAt(responses, "400"), "content", "application/problem+json",
                    "examples", "leaveTypeChangeNotAllowed", "value"))
                    .isEqualTo("LEAVE_TYPE_CHANGE_NOT_ALLOWED");
            assertThat(problemCode(mapAt(responses, "403"), "content", "application/problem+json", "example"))
                    .isEqualTo("ACCESS_DENIED");
            assertThat(problemCode(mapAt(responses, "409"), "content", "application/problem+json",
                    "examples", "invalidTransition", "value"))
                    .isEqualTo("INVALID_TRANSITION");
            assertThat(problemCode(mapAt(responses, "409"), "content", "application/problem+json",
                    "examples", "insufficientBalance", "value"))
                    .isEqualTo("INSUFFICIENT_BALANCE");
        }
    }

    @Test
    void handWrittenContractDocumentsCancellationResponses() throws Exception {
        try (InputStream input = getClass().getResourceAsStream("/openapi.yaml")) {
            assertThat(input).isNotNull();
            Map<?, ?> document = new Yaml().load(input);
            Map<?, ?> paths = (Map<?, ?>) document.get("paths");
            Map<?, ?> cancelPath = (Map<?, ?>) paths.get("/requests/{requestId}/cancel");
            Map<?, ?> cancelOperation = (Map<?, ?>) cancelPath.get("post");
            Map<?, ?> responses = (Map<?, ?>) cancelOperation.get("responses");

            assertThat(responses.keySet().stream().map(Object::toString).toList())
                    .contains("200", "401", "403", "404", "409");
            assertThat(schemaRef(mapAt(responses, "200"), "content", "application/json", "schema"))
                    .isEqualTo("#/components/schemas/LeaveRequest");
            assertThat(problemCode(mapAt(responses, "403"), "content", "application/problem+json", "example"))
                    .isEqualTo("ACCESS_DENIED");
            assertThat(problemCode(mapAt(responses, "409"), "content", "application/problem+json",
                    "examples", "invalidTransition", "value"))
                    .isEqualTo("INVALID_TRANSITION");
            assertThat(problemCode(mapAt(responses, "409"), "content", "application/problem+json",
                    "examples", "missingBalance", "value"))
                    .isEqualTo("INSUFFICIENT_BALANCE");
        }
    }

    @Test
    void handWrittenContractDocumentsRequestHistoryEntryTypesAndFields() throws Exception {
        try (InputStream input = getClass().getResourceAsStream("/openapi.yaml")) {
            assertThat(input).isNotNull();
            Map<?, ?> document = new Yaml().load(input);
            Map<?, ?> paths = (Map<?, ?>) document.get("paths");
            Map<?, ?> historyPath = (Map<?, ?>) paths.get("/requests/{requestId}/history");
            Map<?, ?> historyOperation = (Map<?, ?>) historyPath.get("get");
            Map<?, ?> responses = (Map<?, ?>) historyOperation.get("responses");
            assertThat(responses.keySet().stream().map(Object::toString).toList())
                    .contains("200", "401", "403", "404");
            assertThat(schemaRef(mapAt(responses, "200"), "content", "application/json", "schema", "items"))
                    .isEqualTo("#/components/schemas/HistoryEntry");

            Map<?, ?> components = (Map<?, ?>) document.get("components");
            Map<?, ?> schemas = (Map<?, ?>) components.get("schemas");
            Map<?, ?> historyEntry = (Map<?, ?>) schemas.get("HistoryEntry");
            Map<?, ?> properties = (Map<?, ?>) historyEntry.get("properties");
            assertThat(properties.keySet().stream().map(Object::toString).toList())
                    .contains("type", "actorId", "actorUsername", "occurredAt", "detail");
            assertThat(((List<?>) historyEntry.get("required")).stream()
                            .map(Object::toString)
                            .toList())
                    .containsExactly("type", "actorId", "actorUsername", "occurredAt", "detail");

            Map<?, ?> historyEntryType = (Map<?, ?>) schemas.get("HistoryEntryType");
            List<String> enumValues = ((List<?>) historyEntryType.get("enum")).stream()
                    .map(Object::toString)
                    .toList();
            assertThat(enumValues)
                    .containsExactly("SUBMITTED", "EDITED", "CANCELLED", "APPROVED", "REJECTED", "COMMENT");
        }
    }

    @Test
    void swaggerUiIsReachableUnauthenticated() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
    }

    private String schemaRef(Map<?, ?> operationPart, String... path) {
        Map<?, ?> current = operationPart;
        for (String key : path) {
            current = (Map<?, ?>) current.get(key);
        }
        return (String) current.get("$ref");
    }

    private String problemCode(Map<?, ?> responsePart, String... path) {
        Map<?, ?> current = responsePart;
        for (String key : path) {
            current = (Map<?, ?>) current.get(key);
        }
        return (String) current.get("code");
    }

    private Map<?, ?> mapAt(Map<?, ?> parent, String key) {
        return (Map<?, ?>) parent.get(key);
    }
}
