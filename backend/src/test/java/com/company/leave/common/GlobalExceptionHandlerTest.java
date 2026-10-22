package com.company.leave.common;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves the shared error envelope (AD-11): an access-denied failure and a validation
 * failure both come back as {@code application/problem+json} with the right {@code code},
 * exercised against {@link GlobalExceptionHandler} directly via a standalone, throwaway
 * probe controller (no real controllers exist yet in this story).
 */
class GlobalExceptionHandlerTest {

    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new ProbeController())
            .setControllerAdvice(new GlobalExceptionHandler())
            .setValidator(new LocalValidatorFactoryBean())
            .build();

    @Test
    void accessDeniedFailureReturnsProblemJsonWithAccessDeniedCode() throws Exception {
        mockMvc.perform(get("/probe/denied"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.valueOf("application/problem+json")))
                .andExpect(jsonPath("$.code").value(ErrorCode.ACCESS_DENIED.name()));
    }

    @Test
    void validationFailureReturnsProblemJsonWithValidationFailedCode() throws Exception {
        mockMvc.perform(post("/probe/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.valueOf("application/problem+json")))
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_FAILED.name()));
    }

    @RestController
    static class ProbeController {

        @GetMapping("/probe/denied")
        public void denied() {
            throw new AccessDeniedException("nope");
        }

        @PostMapping("/probe/validate")
        public void validate(@Valid @RequestBody ProbeRequest request) {
        }
    }

    record ProbeRequest(@NotBlank String name) {
    }
}
