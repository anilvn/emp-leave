package com.company.leave.leave;

import java.time.LocalDate;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Value;

@Schema(name = "LeaveRequestUpdate", description = "Payload for editing a pending leave request")
@Value
public class LeaveRequestUpdateRequest {

    @NotNull
    @Schema(description = "Must match the request's existing leave type", example = "1")
    Long leaveTypeId;

    @NotNull
    @Schema(description = "First day of leave (inclusive)", example = "2026-10-30")
    LocalDate startDate;

    @NotNull
    @Schema(description = "Last day of leave (inclusive)", example = "2026-11-03")
    LocalDate endDate;

    @Schema(description = "Optional free text", example = "Trip shortened by a day", nullable = true)
    String notes;
}
