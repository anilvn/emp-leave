package com.company.leave.common.internal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.company.leave.common.ApiException;
import com.company.leave.common.ErrorCodes;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

class GlobalExceptionHandlerTest {

	record Body(@NotBlank String name) {
	}

	@RestController
	static class StubController {

		@GetMapping("/boom")
		String boom() {
			throw new ApiException(HttpStatus.CONFLICT, ErrorCodes.INVALID_TRANSITION, "Already decided");
		}

		@PostMapping("/validate")
		String validate(@Valid @RequestBody Body body) {
			return body.name();
		}
	}

	MockMvc mvc;

	@BeforeEach
	void setUp() {
		LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
		validator.afterPropertiesSet();
		mvc = MockMvcBuilders.standaloneSetup(new StubController())
				.setControllerAdvice(new GlobalExceptionHandler())
				.setValidator(validator)
				.build();
	}

	@Test
	void apiExceptionBecomesProblemDetailWithCode() throws Exception {
		mvc.perform(get("/boom"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("INVALID_TRANSITION"))
				.andExpect(jsonPath("$.status").value(409))
				.andExpect(jsonPath("$.detail").value("Already decided"));
	}

	@Test
	void beanValidationBecomesValidationFailed() throws Exception {
		mvc.perform(post("/validate").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.errors.name").exists());
	}
}
