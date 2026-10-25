package com.company.leave.approvals;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.company.leave.audit.AuditService;
import com.company.leave.balances.BalanceResponse;
import com.company.leave.balances.BalanceService;
import com.company.leave.leave.HistoryEntry;
import com.company.leave.leave.LeaveRequestDecision;
import com.company.leave.leave.LeaveRequestResponse;
import com.company.leave.leave.LeaveRequestService;
import com.company.leave.leave.LeaveTypeResponse;
import com.company.leave.leave.LeaveTypeService;
import com.company.leave.security.AccessPolicy;
import com.company.leave.security.UserScope;
import com.company.leave.users.Role;
import com.company.leave.users.UserService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ApprovalService {

    private final AccessPolicy accessPolicy;
    private final LeaveRequestService leaveRequestService;
    private final BalanceService balanceService;
    private final LeaveTypeService leaveTypeService;
    private final AuditService auditService;
    private final UserService userService;

    ApprovalService(
            AccessPolicy accessPolicy,
            LeaveRequestService leaveRequestService,
            BalanceService balanceService,
            LeaveTypeService leaveTypeService,
            AuditService auditService,
            UserService userService) {
        this.accessPolicy = accessPolicy;
        this.leaveRequestService = leaveRequestService;
        this.balanceService = balanceService;
        this.leaveTypeService = leaveTypeService;
        this.auditService = auditService;
        this.userService = userService;
    }

    @Transactional(readOnly = true)
    public List<ApprovalDetail> pendingApprovals() {
        accessPolicy.requireRole(Role.MANAGER, Role.HR_ADMIN);
        UserScope scope = accessPolicy.currentScope();
        long callerId = accessPolicy.currentUserId();
        List<LeaveRequestResponse> requests =
                leaveRequestService.findPendingApprovalsByScope(scope, callerId);
        if (requests.isEmpty()) {
            return List.of();
        }

        Map<Long, String> employeeUsernames = userService.getUsernamesByIds(
                requests.stream().map(LeaveRequestResponse::getEmployeeId).distinct().toList());
        Map<Long, String> leaveTypeNames = leaveTypeService.listLeaveTypes().stream()
                .collect(Collectors.toMap(LeaveTypeResponse::getId, LeaveTypeResponse::getName));

        return requests.stream()
                .map(request -> toApprovalDetail(
                        request,
                        employeeUsernames.get(request.getEmployeeId()),
                        leaveTypeNames.get(request.getLeaveTypeId())))
                .toList();
    }

    public LeaveRequestResponse approve(long requestId, String comment) {
        long approverId = accessPolicy.currentUserId();
        LeaveRequestResponse request = leaveRequestService.get(requestId);
        if (request.getApproverId() != approverId) {
            throw new AccessDeniedException("Caller is not the assigned approver for request " + requestId);
        }
        return leaveRequestService.applyDecision(
                requestId, LeaveRequestDecision.APPROVED, comment, approverId);
    }

    public LeaveRequestResponse reject(long requestId, String comment) {
        long approverId = accessPolicy.currentUserId();
        LeaveRequestResponse request = leaveRequestService.get(requestId);
        if (request.getApproverId() != approverId) {
            throw new AccessDeniedException("Caller is not the assigned approver for request " + requestId);
        }
        return leaveRequestService.applyDecision(
                requestId, LeaveRequestDecision.REJECTED, comment, approverId);
    }

    private ApprovalDetail toApprovalDetail(
            LeaveRequestResponse request, String employeeUsername, String leaveTypeName) {
        BalanceResponse balance = balanceService.getBalancesForUser(request.getEmployeeId()).stream()
                .filter(candidate -> candidate.getLeaveTypeId() == request.getLeaveTypeId())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "No balance response for leave type " + request.getLeaveTypeId()));

        List<HistoryEntry> history = leaveRequestService.history(request.getId());
        List<AuditService.RequestCommentEvent> commentEvents =
                auditService.findLeaveRequestComments(request.getId());
        Map<Long, String> authorUsernames = userService.getUsernamesByIds(
                commentEvents.stream().map(AuditService.RequestCommentEvent::authorId).distinct().toList());
        List<ApprovalComment> comments = commentEvents.stream()
                .map(event -> ApprovalComment.builder()
                        .id(event.id())
                        .requestId(request.getId())
                        .authorId(event.authorId())
                        .authorUsername(authorUsernames.get(event.authorId()))
                        .body(event.body())
                        .createdAt(event.createdAt())
                        .build())
                .toList();

        return ApprovalDetail.builder()
                .id(request.getId())
                .employeeId(request.getEmployeeId())
                .approverId(request.getApproverId())
                .leaveTypeId(request.getLeaveTypeId())
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .workingDays(request.getWorkingDays())
                .notes(request.getNotes())
                .status(ApprovalDetail.Status.valueOf(request.getStatus()))
                .submittedAt(request.getSubmittedAt())
                .decidedAt(request.getDecidedAt())
                .decisionComment(request.getDecisionComment())
                .employeeUsername(employeeUsername)
                .leaveTypeName(leaveTypeName)
                .balance(balance)
                .comments(comments)
                .history(history)
                .build();
    }
}
