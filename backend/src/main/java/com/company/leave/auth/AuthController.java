package com.company.leave.auth;

import jakarta.validation.Valid;
import com.company.leave.audit.AuditService;
import com.company.leave.common.ApiException;
import com.company.leave.common.ErrorCode;
import com.company.leave.security.AccessPolicy;
import com.company.leave.security.AuthCookies;
import com.company.leave.security.JwtService;
import com.company.leave.security.SessionRegistry;
import com.company.leave.users.Role;
import com.company.leave.users.UserAccount;
import com.company.leave.users.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ProblemDetail;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@Tag(name = "Auth", description = "Login, logout and password change (FR56-FR58)")
public class AuthController {

    private final UserService userService;
    private final SessionRegistry sessionRegistry;
    private final JwtService jwtService;
    private final AuditService auditService;
    private final AccessPolicy accessPolicy;

    AuthController(
            UserService userService,
            SessionRegistry sessionRegistry,
            JwtService jwtService,
            AuditService auditService,
            AccessPolicy accessPolicy) {
        this.userService = userService;
        this.sessionRegistry = sessionRegistry;
        this.jwtService = jwtService;
        this.auditService = auditService;
        this.accessPolicy = accessPolicy;
    }

    @Operation(
            summary = "Log in with username and password",
            description = "Issues the HttpOnly/Secure/SameSite access_token cookie on success (FR56). "
                    + "Lockout is evaluated before any token is issued (FR57, AD-9).")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Login succeeded; access_token cookie set"),
        @ApiResponse(responseCode = "401", description = "Invalid credentials (code: INVALID_CREDENTIALS)",
                content = @Content(mediaType = "application/problem+json",
                        schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(responseCode = "423", description = "Account locked after 5 failed attempts "
                + "(code: ACCOUNT_LOCKED, FR57)",
                content = @Content(mediaType = "application/problem+json",
                        schema = @Schema(implementation = ProblemDetail.class)))
    })
    @SecurityRequirements
    @PostMapping("/login")
    @Transactional(noRollbackFor = ApiException.class)
    public ResponseEntity<UserAccount> login(@Valid @RequestBody LoginRequest request) {
        UserAccount user = userService.authenticate(request.getUsername(), request.getPassword())
                .orElseThrow(() -> new ApiException(
                        HttpStatus.UNAUTHORIZED, ErrorCode.INVALID_CREDENTIALS, "Username or password is incorrect."));

        String tokenId = sessionRegistry.createSession(user.getId());
        String token = jwtService.issue(user.getId(), user.getRole().name(), tokenId);
        ResponseCookie cookie = AuthCookies.issue(token, JwtService.TOKEN_LIFETIME);
        auditService.recordEvent("AUTH", null, "LOGIN", user.getId(), null);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(user);
    }

    @Operation(
            summary = "Log out and clear the auth cookie",
            description = "Revokes every active session for the caller, so an already-issued token "
                    + "cannot be reused (AD-8), and clears the access_token cookie.")
    @ApiResponse(responseCode = "204", description = "Logged out")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(Authentication authentication) {
        accessPolicy.requireRole(Role.EMPLOYEE, Role.MANAGER, Role.HR_ADMIN);
        long userId = (Long) authentication.getPrincipal();
        sessionRegistry.revokeAllForUser(userId);
        ResponseCookie cookie = AuthCookies.clear();
        auditService.recordEvent("AUTH", null, "LOGOUT", userId, null);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .build();
    }

    @Operation(
            summary = "Change the current user's password",
            description = "Required on first login after HR resets a password (FR20) and usable any "
                    + "time after that (FR56). The new password must be at least 8 characters and "
                    + "include both letters and numbers (NFR4).")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Password changed"),
        @ApiResponse(responseCode = "400", description = "New password fails the password rule "
                + "(code: VALIDATION_FAILED)",
                content = @Content(mediaType = "application/problem+json",
                        schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(responseCode = "401", description = "Current password incorrect "
                + "(code: INVALID_CREDENTIALS)",
                content = @Content(mediaType = "application/problem+json",
                        schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PostMapping("/change-password")
    public ResponseEntity<Void> changePassword(
            Authentication authentication, @Valid @RequestBody ChangePasswordRequest request) {
        accessPolicy.requireRole(Role.EMPLOYEE, Role.MANAGER, Role.HR_ADMIN);
        long userId = (Long) authentication.getPrincipal();
        userService.changePassword(userId, request.getCurrentPassword(), request.getNewPassword());
        return ResponseEntity.noContent().build();
    }
}
