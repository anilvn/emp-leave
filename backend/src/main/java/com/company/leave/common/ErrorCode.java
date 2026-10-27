package com.company.leave.common;

/**
 * Machine-readable error codes returned in the {@code code} field of every non-2xx
 * {@code application/problem+json} response (AD-11).
 *
 * <p>This enum must mirror {@code openapi.yaml}'s {@code ProblemDetail.code} enum
 * exactly: the same 11 values, no extras, none missing.
 */
public enum ErrorCode {
    ACCESS_DENIED,
    VALIDATION_FAILED,
    INVALID_TRANSITION,
    INSUFFICIENT_BALANCE,
    NOT_FOUND,
    INVALID_CREDENTIALS,
    LEAVE_TYPE_CHANGE_NOT_ALLOWED,
    COMMENT_REQUIRED,
    ACCOUNT_LOCKED,
    PASSWORD_CHANGE_REQUIRED,
    OVERLAPPING_LEAVE_REQUEST
}
