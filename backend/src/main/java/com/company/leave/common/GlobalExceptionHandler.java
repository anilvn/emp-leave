package com.company.leave.common;

import jakarta.validation.ConstraintViolationException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Produces the one shared {@code application/problem+json} error envelope that every
 * later story's controllers throw into (AD-11).
 *
 * <p>Anything not explicitly handled here falls through to Spring's own built-in
 * {@code ProblemDetail} generation (enabled via {@code spring.mvc.problemdetails.enabled})
 * without a custom {@code code} — no {@link ErrorCode} value fits a genuinely
 * unclassified failure, and inventing one would violate AD-11's "no extra values".
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ProblemDetail handleApiException(ApiException ex) {
        return problemDetail(ex.getStatus(), ex.getMessage(), ex.getErrorCode());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDenied(AccessDeniedException ex) {
        return problemDetail(HttpStatus.FORBIDDEN, "Access is denied", ErrorCode.ACCESS_DENIED);
    }

    @ExceptionHandler({
        MethodArgumentNotValidException.class,
        ConstraintViolationException.class,
        HttpMessageNotReadableException.class
    })
    public ProblemDetail handleValidationFailure(Exception ex) {
        return problemDetail(HttpStatus.BAD_REQUEST, "Validation failed", ErrorCode.VALIDATION_FAILED);
    }

    private ProblemDetail problemDetail(HttpStatus status, String detail, ErrorCode code) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, detail);
        problemDetail.setProperty("code", code.name());
        return problemDetail;
    }
}
