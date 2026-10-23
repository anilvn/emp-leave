package com.company.leave.leave;

import java.time.Instant;
import java.time.LocalDate;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;

@Schema(description = "A leave request")
@Value
@Builder
public class LeaveRequestResponse {

    @Schema(description = "Leave request id", example = "101")
    long id;

    @Schema(description = "Employee who submitted the request", example = "5")
    long employeeId;

    @Schema(description = "Manager or HR who may decide", example = "3")
    long approverId;

    @Schema(description = "Leave type id", example = "1")
    long leaveTypeId;

    @Schema(description = "First day of leave", example = "2026-10-29")
    LocalDate startDate;

    @Schema(description = "Last day of leave", example = "2026-11-03")
    LocalDate endDate;

    @Schema(description = "Mon-Fri working days in range", example = "4")
    int workingDays;

    @Schema(description = "Optional notes", example = "Family trip", nullable = true)
    String notes;

    @Schema(description = "Current status", example = "PENDING")
    String status;

    @Schema(description = "When the request was submitted")
    Instant submittedAt;

    @Schema(description = "When the request was decided", nullable = true)
    Instant decidedAt;

    @Schema(description = "Approver/rejector comment", nullable = true)
    String decisionComment;
}
