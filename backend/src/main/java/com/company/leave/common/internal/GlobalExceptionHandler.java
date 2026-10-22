package com.company.leave.common.internal;

import java.util.LinkedHashMap;
import java.util.Map;

import com.company.leave.common.ApiException;
import com.company.leave.common.ErrorCodes;
import com.company.leave.common.ProblemDetails;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

/** The only exception handler in the application; modules throw {@link ApiException} and never handle it. */
@RestControllerAdvice
class GlobalExceptionHandler {

	@ExceptionHandler(ApiException.class)
	ResponseEntity<ProblemDetail> handleApi(ApiException ex) {
		return ResponseEntity.status(ex.getStatus())
				.body(ProblemDetails.of(ex.getStatus(), ex.getCode(), ex.getMessage()));
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	ResponseEntity<ProblemDetail> handleBeanValidation(MethodArgumentNotValidException ex) {
		Map<String, String> errors = new LinkedHashMap<>();
		for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
			errors.putIfAbsent(fe.getField(), fe.getDefaultMessage());
		}
		ProblemDetail problem = ProblemDetails.of(HttpStatus.BAD_REQUEST, ErrorCodes.VALIDATION_FAILED,
				"Request validation failed");
		problem.setProperty("errors", errors);
		return ResponseEntity.badRequest().body(problem);
	}

	@ExceptionHandler(HandlerMethodValidationException.class)
	ResponseEntity<ProblemDetail> handleMethodValidation(HandlerMethodValidationException ex) {
		return ResponseEntity.badRequest()
				.body(ProblemDetails.of(HttpStatus.BAD_REQUEST, ErrorCodes.VALIDATION_FAILED,
						"Request validation failed"));
	}
}
