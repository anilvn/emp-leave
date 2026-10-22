package com.company.leave.security;

import java.time.Duration;
import java.util.UUID;

import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String SECRET = "company-leave-test-jwt-secret-with-at-least-32-bytes";

    private final JwtService jwtService = new JwtService(SECRET);

    @Test
    void issuesAndParsesSignedClaims() {
        String tokenId = UUID.randomUUID().toString();

        String token = jwtService.issue(5L, "EMPLOYEE", tokenId);

        assertThat(jwtService.parseAndValidate(token))
                .isEqualTo(new JwtService.ParsedToken(5L, "EMPLOYEE", tokenId));
    }

    @Test
    void rejectsTamperedSignature() {
        String token = jwtService.issue(5L, "EMPLOYEE", UUID.randomUUID().toString());
        char replacement = token.charAt(token.length() - 1) == 'a' ? 'b' : 'a';
        String tampered = token.substring(0, token.length() - 1) + replacement;

        assertThatThrownBy(() -> jwtService.parseAndValidate(tampered)).isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsExpiredToken() {
        String token = jwtService.issue(5L, "EMPLOYEE", UUID.randomUUID().toString(), Duration.ofSeconds(-1));

        assertThatThrownBy(() -> jwtService.parseAndValidate(token)).isInstanceOf(JwtException.class);
    }
}
