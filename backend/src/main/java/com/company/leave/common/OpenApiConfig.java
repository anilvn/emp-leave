package com.company.leave.common;

import java.util.List;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Springdoc base document: API info, the HTTPS proxy server (FR55), and the {@code cookieAuth}
 * security scheme matching the HttpOnly/Secure/SameSite {@code access_token} cookie (AD-7, NFR5).
 * Cross-cutting config only — no entity, repository or business-rule service (AD-3).
 */
@Configuration
public class OpenApiConfig {

    static final String COOKIE_AUTH = "cookieAuth";

    @Bean
    public OpenAPI companyLeaveOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Employee Leave Tracking System API (POC)")
                        .version("0.1.0")
                        .description("REST API for the Employee Leave Tracking System POC. "
                                + "This live document is generated from the controllers; the committed "
                                + "openapi.yaml remains the binding hand-written contract.")
                        .contact(new Contact().name("Anil")))
                .servers(List.of(
                        new Server()
                                .url("http://localhost:8080")
                                .description("Backend directly (run the Spring Boot app; use this for Try it out)"),
                        new Server()
                                .url("https://localhost/api")
                                .description("Full stack behind the HTTPS reverse proxy (FR55)")))
                .components(new Components().addSecuritySchemes(COOKIE_AUTH, new SecurityScheme()
                        .type(SecurityScheme.Type.APIKEY)
                        .in(SecurityScheme.In.COOKIE)
                        .name("access_token")
                        .description("Session cookie set by POST /auth/login. HttpOnly, Secure, "
                                + "SameSite=Strict; 30-minute inactivity timeout (FR58). Not a bearer "
                                + "token in JavaScript-readable storage.")))
                .addSecurityItem(new SecurityRequirement().addList(COOKIE_AUTH));
    }
}
