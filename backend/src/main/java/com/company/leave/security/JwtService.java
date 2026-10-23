package com.company.leave.security;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;

import javax.crypto.SecretKey;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    /**
     * Token lifetime, matched to the {@link SessionRegistry#IDLE_TIMEOUT 30-minute idle window}:
     * the token must stay valid across an allowed idle gap, and {@link JwtAuthenticationFilter}
     * re-issues it on every authenticated request so an active user is never cut off early (FR58,
     * AD-8). This supersedes AD-7's original 15-minute figure, which could not coexist with a
     * 30-minute idle timeout without a refresh step.
     */
    public static final Duration TOKEN_LIFETIME = Duration.ofMinutes(30);

    private final SecretKey key;

    JwtService(@Value("${security.jwt.secret}") String secret) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    public String issue(long userId, String role, String tokenId) {
        return issue(userId, role, tokenId, TOKEN_LIFETIME);
    }

    String issue(long userId, String role, String tokenId, Duration lifetime) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(Long.toString(userId))
                .claim("role", role)
                .claim("token_id", tokenId)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(lifetime)))
                .signWith(key)
                .compact();
    }

    public ParsedToken parseAndValidate(String token) {
        if (token == null || token.isBlank()) {
            throw new JwtException("JWT must not be empty");
        }
        var claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
        try {
            long userId = Long.parseLong(claims.getSubject());
            String role = claims.get("role", String.class);
            String tokenId = claims.get("token_id", String.class);
            if (role == null || tokenId == null || tokenId.isBlank()) {
                throw new JwtException("JWT is missing required claims");
            }
            return new ParsedToken(userId, role, tokenId);
        } catch (NumberFormatException ex) {
            throw new JwtException("JWT subject is not a user id", ex);
        }
    }

    public record ParsedToken(long userId, String role, String tokenId) {
    }
}
