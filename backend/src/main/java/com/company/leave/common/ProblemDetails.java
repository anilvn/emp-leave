package com.company.leave.common;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

/** Builds the one error body shape shared by the exception handler and the security handlers. */
public final class ProblemDetails {

	private ProblemDetails() {
	}

	public static ProblemDetail of(HttpStatus status, String code, String detail) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
		problem.setTitle(status.getReasonPhrase());
		problem.setProperty("code", code);
		return problem;
	}
}
