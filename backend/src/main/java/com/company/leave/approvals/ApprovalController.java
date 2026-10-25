package com.company.leave.approvals;

import java.util.List;

import com.company.leave.leave.LeaveRequestResponse;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/approvals")
@Tag(name = "Approvals", description = "Manager and HR views of pending decisions")
public class ApprovalController {

    private final ApprovalService approvalService;

    ApprovalController(ApprovalService approvalService) {
        this.approvalService = approvalService;
    }

    @Operation(
            summary = "Pending Approvals dashboard",
            description = "Returns requests assigned to the authenticated manager or HR user with full decision context.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Pending requests with complete approval context",
                content = @Content(mediaType = "application/json",
                        array = @ArraySchema(schema = @Schema(implementation = ApprovalDetail.class)))),
        @ApiResponse(responseCode = "401", description = "No valid access_token cookie"),
        @ApiResponse(responseCode = "403", description = "Caller is not an approver",
                content = @Content(mediaType = "application/problem+json",
                        schema = @Schema(implementation = ProblemDetail.class)))
    })
    @GetMapping("/pending")
    public List<ApprovalDetail> pendingApprovals() {
        return approvalService.pendingApprovals();
    }

    @Operation(summary = "Approve a pending request",
            description = "Approves a request assigned to the authenticated caller. A comment is optional.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Approved request",
                content = @Content(mediaType = "application/json",
                        schema = @Schema(implementation = LeaveRequestResponse.class))),
        @ApiResponse(responseCode = "401", description = "No valid access_token cookie"),
        @ApiResponse(responseCode = "400", description = "Malformed approval request body",
                content = @Content(mediaType = "application/problem+json",
                        schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(responseCode = "403", description = "Caller is not this request's assigned approver",
                content = @Content(mediaType = "application/problem+json",
                        schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(responseCode = "404", description = "Request does not exist",
                content = @Content(mediaType = "application/problem+json",
                        schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(responseCode = "409", description = "Request is no longer pending",
                content = @Content(mediaType = "application/problem+json",
                        schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PostMapping("/{requestId}/approve")
    public LeaveRequestResponse approve(
            @Parameter(description = "Leave request id", required = true) @PathVariable long requestId,
            @org.springframework.web.bind.annotation.RequestBody(required = false)
                    @io.swagger.v3.oas.annotations.parameters.RequestBody(
                            required = false,
                            content = @Content(schema = @Schema(implementation = ApprovalDecisionRequest.class)))
                    ApprovalDecisionRequest decision) {
        return approvalService.approve(requestId, decision == null ? null : decision.getComment());
    }
}
