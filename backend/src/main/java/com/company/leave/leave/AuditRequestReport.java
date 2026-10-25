package com.company.leave.leave;

import java.time.LocalDate;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;

@Schema(name = "AuditRequestReport",
        description = "One leave request in the HR audit trail, with full comment thread and history timeline (FR42-FR45, FR15)")
@Value
@Builder
public class AuditRequestReport {

    long requestId;
    long employeeId;
    String employeeUsername;
    long leaveTypeId;
    String leaveTypeName;
    LocalDate startDate;
    LocalDate endDate;
    int workingDays;
    String status;
    List<Comment> comments;
    List<HistoryEntry> history;
}
