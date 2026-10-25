package com.company.leave.team;

import java.time.LocalDate;
import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/team")
@Tag(name = "Team", description = "Manager's view of their team's approved leave")
public class TeamLeaveController {

    private final TeamLeaveService teamLeaveService;

    TeamLeaveController(TeamLeaveService teamLeaveService) {
        this.teamLeaveService = teamLeaveService;
    }

    @Operation(
            summary = "Team leave by date",
            description = "Returns the manager's direct reports' approved leave, optionally filtered to a date range. "
                    + "Each entry includes the request's full comment thread and history timeline (FR14, FR23).")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Approved team leave entries",
                content = @Content(mediaType = "application/json",
                        array = @ArraySchema(schema = @Schema(implementation = TeamLeaveEntry.class)))),
        @ApiResponse(responseCode = "401", description = "No valid access_token cookie"),
        @ApiResponse(responseCode = "403", description = "Caller does not have the MANAGER role",
                content = @Content(mediaType = "application/problem+json",
                        schema = @Schema(implementation = ProblemDetail.class)))
    })
    @GetMapping("/leave")
    public List<TeamLeaveEntry> teamLeave(
            @Parameter(description = "Start of date range (inclusive). Omit for no lower bound.")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = "End of date range (inclusive). Omit for no upper bound.")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return teamLeaveService.getTeamLeave(from, to);
    }
}
