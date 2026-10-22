package com.company.leave.auth;

import java.time.Duration;

import jakarta.validation.Valid;
import com.company.leave.audit.AuditService;
import com.company.leave.common.ApiException;
import com.company.leave.common.ErrorCode;
import com.company.leave.security.JwtService;
import com.company.leave.security.SessionRegistry;
import com.company.leave.users.UserAccount;
import com.company.leave.users.UserService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private static final String ACCESS_TOKEN_COOKIE = "access_token";
    private static final Duration TOKEN_LIFETIME = Duration.ofMinutes(15);

    private final UserService userService;
    private final SessionRegistry sessionRegistry;
    private final JwtService jwtService;
    private final AuditService auditService;

    AuthController(
            UserService userService,
            SessionRegistry sessionRegistry,
            JwtService jwtService,
            AuditService auditService) {
        this.userService = userService;
        this.sessionRegistry = sessionRegistry;
        this.jwtService = jwtService;
        this.auditService = auditService;
    }

    @PostMapping("/login")
    public ResponseEntity<UserAccount> login(@Valid @RequestBody LoginRequest request) {
        UserAccount user = userService.authenticate(request.getUsername(), request.getPassword())
                .orElseThrow(() -> new ApiException(
                        HttpStatus.UNAUTHORIZED, ErrorCode.INVALID_CREDENTIALS, "Username or password is incorrect."));

        String tokenId = sessionRegistry.createSession(user.getId());
        String token = jwtService.issue(user.getId(), user.getRole().name(), tokenId);
        ResponseCookie cookie = ResponseCookie.from(ACCESS_TOKEN_COOKIE, token)
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path("/")
                .maxAge(TOKEN_LIFETIME)
                .build();
        auditService.recordEvent("AUTH", null, "LOGIN", user.getId(), null);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(user);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(Authentication authentication) {
        long userId = (Long) authentication.getPrincipal();
        sessionRegistry.revokeAllForUser(userId);
        ResponseCookie cookie = ResponseCookie.from(ACCESS_TOKEN_COOKIE, "")
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path("/")
                .maxAge(Duration.ZERO)
                .build();
        auditService.recordEvent("AUTH", null, "LOGOUT", userId, null);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .build();
    }
}
