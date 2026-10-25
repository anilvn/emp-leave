package com.company.leave.balances;

import java.util.List;

import com.company.leave.security.AccessPolicy;
import com.company.leave.users.Role;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/balances")
@Tag(name = "Balances", description = "Leave balance queries — personal view for all roles, full view for HR")
public class BalanceController {

    private final BalanceService balanceService;
    private final AccessPolicy accessPolicy;

    BalanceController(BalanceService balanceService, AccessPolicy accessPolicy) {
        this.balanceService = balanceService;
        this.accessPolicy = accessPolicy;
    }

    @Operation(
            summary = "Get all balances",
            description = "HR_ADMIN only (FR27). Returns every user's balance for every leave type. "
                    + "Optionally filtered by userId.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Balances, optionally filtered by user"),
        @ApiResponse(responseCode = "401", description = "No valid access_token cookie"),
        @ApiResponse(responseCode = "403", description = "Caller is not HR_ADMIN",
                content = @Content(mediaType = "application/problem+json",
                        schema = @Schema(implementation = ProblemDetail.class)))
    })
    @GetMapping
    public ResponseEntity<List<BalanceResponse>> getAllBalances(
            @RequestParam(required = false) Long userId) {
        accessPolicy.requireRole(Role.HR_ADMIN);
        return ResponseEntity.ok(balanceService.getAllBalances(userId));
    }

    @Operation(
            summary = "Get my leave balances",
            description = "Available to employees, managers and HR administrators. Returns every leave type; "
                    + "a missing balance row is returned with zero day counts.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "One balance for every leave type"),
        @ApiResponse(responseCode = "401", description = "No valid access_token cookie"),
        @ApiResponse(responseCode = "403", description = "Password change is required",
                content = @Content(mediaType = "application/problem+json",
                        schema = @Schema(implementation = ProblemDetail.class)))
    })
    @GetMapping("/me")
    public ResponseEntity<List<BalanceResponse>> getMyBalances(Authentication authentication) {
        accessPolicy.requireRole(Role.EMPLOYEE, Role.MANAGER, Role.HR_ADMIN);
        long userId = ((Number) authentication.getPrincipal()).longValue();
        return ResponseEntity.ok(balanceService.getBalancesForUser(userId));
    }
}
