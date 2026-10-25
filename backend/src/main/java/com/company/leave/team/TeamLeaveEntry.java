package com.company.leave.team;

import java.time.LocalDate;
import java.util.List;

import com.company.leave.leave.Comment;
import com.company.leave.leave.HistoryEntry;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;

@Schema(name = "TeamLeaveEntry", description = "An approved leave entry in the team calendar view")
@Value
@Builder
public class TeamLeaveEntry {

    long requestId;
    long employeeId;
    String employeeUsername;
    long leaveTypeId;
    LocalDate startDate;
    LocalDate endDate;
    int workingDays;
    String status;
    List<Comment> comments;
    List<HistoryEntry> history;
}
