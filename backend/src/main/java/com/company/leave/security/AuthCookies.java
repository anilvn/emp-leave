package com.company.leave.security;

import java.time.Duration;

import org.springframework.http.ResponseCookie;

/**
 * Single source of truth for the {@code access_token} cookie attributes (HttpOnly, Secure,
 * SameSite=Strict, root path) so login, logout and the sliding re-issue in
 * {@link JwtAuthenticationFilter} cannot drift apart (NFR5, AD-7).
 */
public final class AuthCookies {

    public static final String ACCESS_TOKEN = "access_token";

    private AuthCookies() {}

    public static ResponseCookie issue(String token, Duration maxAge) {
        return base(token).maxAge(maxAge).build();
    }

    public static ResponseCookie clear() {
        return base("").maxAge(Duration.ZERO).build();
    }

    private static ResponseCookie.ResponseCookieBuilder base(String value) {
        return ResponseCookie.from(ACCESS_TOKEN, value)
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path("/");
    }
}
