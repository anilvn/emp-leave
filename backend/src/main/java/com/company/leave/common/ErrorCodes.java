package com.company.leave.common;

/** Stable machine-readable error codes returned in the ProblemDetail {@code code} field. */
public final class ErrorCodes {

	public static final String VALIDATION_FAILED = "VALIDATION_FAILED";
	public static final String COMMENT_REQUIRED = "COMMENT_REQUIRED";
	public static final String LEAVE_TYPE_CHANGE_NOT_ALLOWED = "LEAVE_TYPE_CHANGE_NOT_ALLOWED";
	public static final String UNAUTHENTICATED = "UNAUTHENTICATED";
	public static final String INVALID_CREDENTIALS = "INVALID_CREDENTIALS";
	public static final String ACCESS_DENIED = "ACCESS_DENIED";
	public static final String CSRF_INVALID = "CSRF_INVALID";
	public static final String PASSWORD_CHANGE_REQUIRED = "PASSWORD_CHANGE_REQUIRED";
	public static final String NOT_FOUND = "NOT_FOUND";
	public static final String INSUFFICIENT_BALANCE = "INSUFFICIENT_BALANCE";
	public static final String INVALID_TRANSITION = "INVALID_TRANSITION";
	public static final String OVERLAPPING_REQUEST = "OVERLAPPING_REQUEST";

	private ErrorCodes() {
	}
}
