package com.company.leave.common;

import org.springframework.http.HttpStatus;

/**
 * Base for every error a module raises on purpose. The single exception handler
 * turns it into an RFC 9457 ProblemDetail carrying {@link #getCode()}.
 */
public class ApiException extends RuntimeException {

	private final HttpStatus status;
	private final String code;

	public ApiException(HttpStatus status, String code) {
		this(status, code, code);
	}

	public ApiException(HttpStatus status, String code, String message) {
		super(message);
		this.status = status;
		this.code = code;
	}

	public HttpStatus getStatus() {
		return status;
	}

	public String getCode() {
		return code;
	}
}
