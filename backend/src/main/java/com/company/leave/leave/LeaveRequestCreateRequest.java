package com.company.leave.leave;

import java.time.LocalDate;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Value;

@Schema(description = "Payload for submitting a new leave request")
@Value
public class LeaveRequestCreateRequest {

    @NotNull
    @Schema(description = "References leave_types (Vacation, Sick, Parental, Sabbatical)", example = "1")
    Long leaveTypeId;

    @NotNull
    @Schema(description = "First day of leave (inclusive)", example = "2026-10-29")
    LocalDate startDate;

    @NotNull
    @Schema(description = "Last day of leave (inclusive); must be on or after startDate", example = "2026-11-03")
    LocalDate endDate;

    @Schema(description = "Optional free text", example = "Family trip", nullable = true)
    String notes;
}
