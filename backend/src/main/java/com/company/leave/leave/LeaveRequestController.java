package com.company.leave.leave;

import com.company.leave.security.AccessPolicy;
import com.company.leave.users.Role;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/requests")
@Tag(name = "Leave Requests", description = "Submit and manage leave requests")
public class LeaveRequestController {

    private final LeaveRequestService leaveRequestService;
    private final AccessPolicy accessPolicy;

    LeaveRequestController(LeaveRequestService leaveRequestService, AccessPolicy accessPolicy) {
        this.leaveRequestService = leaveRequestService;
        this.accessPolicy = accessPolicy;
    }

    @Operation(
            summary = "Submit a leave request",
            description = "Working days are computed server-side (Mon-Fri inclusive, holidays ignored). "
                    + "Reserves the requested days against the employee's balance; refused if days "
                    + "exceed what's available.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Request created, status PENDING"),
        @ApiResponse(responseCode = "400", description = "Validation failed",
                content = @Content(mediaType = "application/problem+json",
                        schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(responseCode = "401", description = "No valid access_token cookie"),
        @ApiResponse(responseCode = "403", description = "Password change is required",
                content = @Content(mediaType = "application/problem+json",
                        schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(responseCode = "409", description = "Insufficient balance",
                content = @Content(mediaType = "application/problem+json",
                        schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PostMapping
    public ResponseEntity<LeaveRequestResponse> submitLeaveRequest(
            @Valid @RequestBody LeaveRequestCreateRequest request,
            Authentication authentication) {
        accessPolicy.requireRole(Role.EMPLOYEE, Role.MANAGER, Role.HR_ADMIN);
        long userId = ((Number) authentication.getPrincipal()).longValue();
        LeaveRequestResponse response = leaveRequestService.submit(request, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
