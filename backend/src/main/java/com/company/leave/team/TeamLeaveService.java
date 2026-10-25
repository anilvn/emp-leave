package com.company.leave.team;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import com.company.leave.leave.Comment;
import com.company.leave.leave.HistoryEntry;
import com.company.leave.leave.LeaveRequestResponse;
import com.company.leave.leave.LeaveRequestService;
import com.company.leave.security.AccessPolicy;
import com.company.leave.users.Role;
import com.company.leave.users.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TeamLeaveService {

    private final AccessPolicy accessPolicy;
    private final LeaveRequestService leaveRequestService;
    private final UserService userService;

    TeamLeaveService(
            AccessPolicy accessPolicy,
            LeaveRequestService leaveRequestService,
            UserService userService) {
        this.accessPolicy = accessPolicy;
        this.leaveRequestService = leaveRequestService;
        this.userService = userService;
    }

    @Transactional(readOnly = true)
    public List<TeamLeaveEntry> getTeamLeave(LocalDate from, LocalDate to) {
        accessPolicy.requireRole(Role.MANAGER);
        long managerId = accessPolicy.currentUserId();
        List<LeaveRequestResponse> requests =
                leaveRequestService.getApprovedTeamLeaveInRange(managerId, from, to);
        if (requests.isEmpty()) {
            return List.of();
        }
        Map<Long, String> employeeUsernames = userService.getUsernamesByIds(
                requests.stream().map(LeaveRequestResponse::getEmployeeId).distinct().toList());
        return requests.stream()
                .map(request -> toTeamLeaveEntry(request, employeeUsernames.get(request.getEmployeeId())))
                .toList();
    }

    private TeamLeaveEntry toTeamLeaveEntry(LeaveRequestResponse request, String employeeUsername) {
        List<Comment> comments = leaveRequestService.listComments(request.getId());
        List<HistoryEntry> history = leaveRequestService.history(request.getId());
        return TeamLeaveEntry.builder()
                .requestId(request.getId())
                .employeeId(request.getEmployeeId())
                .employeeUsername(employeeUsername)
                .leaveTypeId(request.getLeaveTypeId())
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .workingDays(request.getWorkingDays())
                .status(request.getStatus())
                .comments(comments)
                .history(history)
                .build();
    }
}
