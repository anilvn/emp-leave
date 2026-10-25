package com.company.leave.leave;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/audit")
@Tag(name = "Audit", description = "HR audit report — one entry per leave request, any status")
public class AuditController {

    private final LeaveRequestService leaveRequestService;

    AuditController(LeaveRequestService leaveRequestService) {
        this.leaveRequestService = leaveRequestService;
    }

    @Operation(
            summary = "Audit trail",
            description = "HR view only. Returns one AuditRequestReport per leave request — any status — newest "
                    + "submission first, with each request's full comment thread and History timeline nested. "
                    + "No filters; a single call returns the full audit trail (FR42-FR45, FR24).")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Full audit report, one entry per leave request, newest first",
                content = @Content(mediaType = "application/json",
                        array = @ArraySchema(schema = @Schema(implementation = AuditRequestReport.class)))),
        @ApiResponse(responseCode = "401", description = "No valid access_token cookie"),
        @ApiResponse(responseCode = "403", description = "Only HR can view the audit trail",
                content = @Content(mediaType = "application/problem+json",
                        schema = @Schema(implementation = ProblemDetail.class)))
    })
    @GetMapping
    public List<AuditRequestReport> auditTrail() {
        return leaveRequestService.getAuditReport();
    }
}
