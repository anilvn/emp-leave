package com.company.leave.common;

import org.springframework.http.HttpStatus;

/**
 * The one exception every later module's controllers/services throw into the shared
 * error envelope (AD-11).
 *
 * <p>The HTTP status is carried explicitly rather than inferred from the {@link ErrorCode},
 * because the contract maps the same code to different statuses at different call sites
 * (e.g. {@code INSUFFICIENT_BALANCE} appears at both 403 and 409 in {@code openapi.yaml}).
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final ErrorCode errorCode;

    public ApiException(HttpStatus status, ErrorCode errorCode, String message) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
