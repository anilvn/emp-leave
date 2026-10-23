package com.company.leave.users;

import java.util.List;

import jakarta.validation.Valid;
import com.company.leave.security.AccessPolicy;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ProblemDetail;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users")
@Tag(name = "Users", description = "HR account administration (FR18-FR20, FR24)")
public class UserController {

    private final UserService userService;
    private final AccessPolicy accessPolicy;

    UserController(UserService userService, AccessPolicy accessPolicy) {
        this.userService = userService;
        this.accessPolicy = accessPolicy;
    }

    @Operation(
            summary = "Create a user account",
            description = "HR only. Assigns a role and a manager and stores an HR-typed temporary "
                    + "password, BCrypt-hashed on save with must_change_password set true (FR18, FR19). "
                    + "managerId is required unless the role is HR_ADMIN and must reference a MANAGER "
                    + "or HR_ADMIN user.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Created user; must change password on first login"),
        @ApiResponse(responseCode = "400", description = "Missing field, managerId does not match the "
                + "chosen role, or username already taken (code: VALIDATION_FAILED)",
                content = @Content(mediaType = "application/problem+json",
                        schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(responseCode = "403", description = "Caller is not HR (code: ACCESS_DENIED)",
                content = @Content(mediaType = "application/problem+json",
                        schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PostMapping
    public ResponseEntity<UserResponse> createUser(
            Authentication authentication, @Valid @RequestBody UserCreateRequest request) {
        accessPolicy.requireRole(Role.HR_ADMIN);
        long actorId = (Long) authentication.getPrincipal();
        UserResponse created = userService.createUser(request, actorId);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @Operation(
            summary = "List user accounts",
            description = "HR only; no scope filter applied (FR24).")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "All user accounts"),
        @ApiResponse(responseCode = "403", description = "Caller is not HR (code: ACCESS_DENIED)",
                content = @Content(mediaType = "application/problem+json",
                        schema = @Schema(implementation = ProblemDetail.class)))
    })
    @GetMapping
    public ResponseEntity<List<UserResponse>> listUsers() {
        accessPolicy.requireRole(Role.HR_ADMIN);
        return ResponseEntity.ok(userService.listUsers());
    }

    @Operation(
            summary = "Manager dropdown options for the create-account form",
            description = "HR only. Returns every MANAGER plus every HR_ADMIN, since a new top-level "
                    + "manager reports to an HR user (FR19). Backs the Manager field on the "
                    + "create-account form with real options rather than free text.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Users selectable as a manager"),
        @ApiResponse(responseCode = "403", description = "Caller is not HR (code: ACCESS_DENIED)",
                content = @Content(mediaType = "application/problem+json",
                        schema = @Schema(implementation = ProblemDetail.class)))
    })
    @GetMapping("/managers")
    public ResponseEntity<List<ManagerOptionResponse>> listManagerOptions() {
        accessPolicy.requireRole(Role.HR_ADMIN);
        return ResponseEntity.ok(userService.listManagerOptions());
    }

    @Operation(
            summary = "Reset a user's password",
            description = "HR only. Generates a server-side temporary password, BCrypt-hashes it into "
                    + "the target's account with must_change_password set true, and revokes the "
                    + "target's active sessions (FR20, AD-8). The generated value is never logged.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Temporary password issued"),
        @ApiResponse(responseCode = "403", description = "Caller is not HR (code: ACCESS_DENIED)",
                content = @Content(mediaType = "application/problem+json",
                        schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(responseCode = "404", description = "No such user (code: NOT_FOUND)",
                content = @Content(mediaType = "application/problem+json",
                        schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PostMapping("/{userId}/reset-password")
    public ResponseEntity<TemporaryPasswordResponse> resetPassword(
            Authentication authentication, @PathVariable long userId) {
        accessPolicy.requireRole(Role.HR_ADMIN);
        long actorId = (Long) authentication.getPrincipal();
        String temporaryPassword = userService.resetPassword(userId, actorId);
        return ResponseEntity.ok(TemporaryPasswordResponse.builder()
                .temporaryPassword(temporaryPassword)
                .build());
    }
}
