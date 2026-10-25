package com.company.leave.approvals;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.company.leave.balances.BalanceResponse;
import com.company.leave.leave.Comment;
import com.company.leave.leave.HistoryEntry;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;

@Schema(
        name = "ApprovalDetail",
        description = "Pending request and all context needed to decide it",
        requiredProperties = {
                "id", "employeeId", "approverId", "leaveTypeId", "startDate", "endDate",
                "workingDays", "notes", "status", "submittedAt", "decidedAt", "decisionComment",
                "employeeUsername", "leaveTypeName", "balance", "comments", "history"
        })
@Value
@Builder
public class ApprovalDetail {

    public enum Status {
        PENDING,
        APPROVED,
        REJECTED,
        CANCELLED
    }

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    long id;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    long employeeId;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    long approverId;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    long leaveTypeId;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    LocalDate startDate;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    LocalDate endDate;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    int workingDays;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
    String notes;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    Status status;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    Instant submittedAt;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
    Instant decidedAt;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
    String decisionComment;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    String employeeUsername;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    String leaveTypeName;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    BalanceResponse balance;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    List<Comment> comments;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    List<HistoryEntry> history;
}
