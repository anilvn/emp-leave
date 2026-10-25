package com.company.leave.leave;

import java.util.List;

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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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

    @Operation(summary = "List leave requests",
            description = "Returns the caller's permitted requests, optionally filtered by status and employee.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Matching leave requests"),
        @ApiResponse(responseCode = "400", description = "Invalid status or employee id",
                content = @Content(mediaType = "application/problem+json",
                        schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(responseCode = "401", description = "No valid access_token cookie"),
        @ApiResponse(responseCode = "403", description = "Requested employee is outside the caller's scope",
                content = @Content(mediaType = "application/problem+json",
                        schema = @Schema(implementation = ProblemDetail.class)))
    })
    @GetMapping
    public List<LeaveRequestResponse> listLeaveRequests(
            @RequestParam(required = false) RequestStatus status,
            @RequestParam(required = false) Long employeeId) {
        return leaveRequestService.list(status, employeeId);
    }

    @Operation(summary = "Get one leave request",
            description = "Visible to the request owner, the owner's direct manager, and HR.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "The leave request"),
        @ApiResponse(responseCode = "401", description = "No valid access_token cookie"),
        @ApiResponse(responseCode = "403", description = "Caller is outside the request's permitted scope",
                content = @Content(mediaType = "application/problem+json",
                        schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(responseCode = "404", description = "Request not found",
                content = @Content(mediaType = "application/problem+json",
                        schema = @Schema(implementation = ProblemDetail.class)))
    })
    @GetMapping("/{requestId}")
    public LeaveRequestResponse getLeaveRequest(@PathVariable long requestId) {
        return leaveRequestService.get(requestId);
    }

    @Operation(
            summary = "Get leave request history",
            description = "Returns the request timeline, visible to its owner, the owner's direct manager, and HR.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Chronological request history"),
        @ApiResponse(responseCode = "401", description = "No valid access_token cookie"),
        @ApiResponse(responseCode = "403", description = "Caller is outside the request's permitted scope",
                content = @Content(mediaType = "application/problem+json",
                        schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(responseCode = "404", description = "Request not found",
                content = @Content(mediaType = "application/problem+json",
                        schema = @Schema(implementation = ProblemDetail.class)))
    })
    @GetMapping("/{requestId}/history")
    public List<HistoryEntry> getLeaveRequestHistory(@PathVariable long requestId) {
        return leaveRequestService.history(requestId);
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

    @Operation(
            summary = "Modify a pending leave request",
            description = "Only the request owner may edit a pending request. Leave type must remain unchanged.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Updated request",
                content = @Content(mediaType = "application/json",
                        schema = @Schema(implementation = LeaveRequestResponse.class))),
        @ApiResponse(responseCode = "400", description = "Validation failed or leave type changed",
                content = @Content(mediaType = "application/problem+json",
                        schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(responseCode = "403", description = "Caller is not the request owner",
                content = @Content(mediaType = "application/problem+json",
                        schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(responseCode = "404", description = "Request not found",
                content = @Content(mediaType = "application/problem+json",
                        schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(responseCode = "409", description = "Request is terminal or balance is insufficient",
                content = @Content(mediaType = "application/problem+json",
                        schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PutMapping("/{requestId}")
    public LeaveRequestResponse updateLeaveRequest(
            @PathVariable long requestId,
            @Valid @RequestBody LeaveRequestUpdateRequest request) {
        return leaveRequestService.update(requestId, request);
    }

    @Operation(
            summary = "List comments on a leave request",
            description = "Returns all discussion comments, oldest first. Visible to the request owner, "
                    + "the owner's direct manager, and HR.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "All comments on the request, oldest first"),
        @ApiResponse(responseCode = "401", description = "No valid access_token cookie"),
        @ApiResponse(responseCode = "403", description = "Caller is outside the request's permitted scope",
                content = @Content(mediaType = "application/problem+json",
                        schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(responseCode = "404", description = "Request not found",
                content = @Content(mediaType = "application/problem+json",
                        schema = @Schema(implementation = ProblemDetail.class)))
    })
    @GetMapping("/{requestId}/comments")
    public List<Comment> listComments(@PathVariable long requestId) {
        return leaveRequestService.listComments(requestId);
    }

    @Operation(
            summary = "Add a discussion comment to a leave request",
            description = "Posts a comment visible to the request owner, the owner's direct manager, and HR. "
                    + "Comments may be added regardless of request status. An immutable audit event is written.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Comment created"),
        @ApiResponse(responseCode = "400", description = "Blank or missing body",
                content = @Content(mediaType = "application/problem+json",
                        schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(responseCode = "401", description = "No valid access_token cookie"),
        @ApiResponse(responseCode = "403", description = "Caller is outside the request's permitted scope",
                content = @Content(mediaType = "application/problem+json",
                        schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(responseCode = "404", description = "Request not found",
                content = @Content(mediaType = "application/problem+json",
                        schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PostMapping("/{requestId}/comments")
    public ResponseEntity<Comment> addComment(
            @PathVariable long requestId,
            @Valid @RequestBody CommentCreateRequest request) {
        Comment comment = leaveRequestService.addComment(requestId, request.getBody());
        return ResponseEntity.status(HttpStatus.CREATED).body(comment);
    }

    @Operation(
            summary = "Cancel a pending leave request",
            description = "Only the request owner may cancel a pending request. Reserved days are released.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Cancelled request",
                content = @Content(mediaType = "application/json",
                        schema = @Schema(implementation = LeaveRequestResponse.class))),
        @ApiResponse(responseCode = "401", description = "No valid access_token cookie"),
        @ApiResponse(responseCode = "403", description = "Caller is not the request owner",
                content = @Content(mediaType = "application/problem+json",
                        schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(responseCode = "404", description = "Request not found",
                content = @Content(mediaType = "application/problem+json",
                        schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(responseCode = "409", description = "Request is no longer PENDING or its balance row is missing",
                content = @Content(mediaType = "application/problem+json",
                        schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PostMapping("/{requestId}/cancel")
    public LeaveRequestResponse cancelLeaveRequest(@PathVariable long requestId) {
        return leaveRequestService.cancel(requestId);
    }
}
