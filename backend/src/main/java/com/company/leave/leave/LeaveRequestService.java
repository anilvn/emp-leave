package com.company.leave.leave;

import java.time.Instant;

import com.company.leave.audit.AuditService;
import com.company.leave.balances.BalanceService;
import com.company.leave.common.ApiException;
import com.company.leave.common.ErrorCode;
import com.company.leave.common.WorkingDayCalculator;
import com.company.leave.notifications.NotificationKind;
import com.company.leave.notifications.NotificationMessage;
import com.company.leave.notifications.NotificationService;
import com.company.leave.users.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Orchestrates leave request submission: validates dates, computes working days,
 * checks balance availability via {@link BalanceService}, inserts the request,
 * reserves days, writes a ledger row and records an audit event — all in one
 * transaction (AD-4).
 */
@Service
public class LeaveRequestService {

    private final LeaveRequestRepository leaveRequestRepository;
    private final LeaveTypeService leaveTypeService;
    private final BalanceService balanceService;
    private final UserService userService;
    private final AuditService auditService;
    private final NotificationService notificationService;

    LeaveRequestService(LeaveRequestRepository leaveRequestRepository,
                        LeaveTypeService leaveTypeService,
                        BalanceService balanceService,
                        UserService userService,
                        AuditService auditService,
                        NotificationService notificationService) {
        this.leaveRequestRepository = leaveRequestRepository;
        this.leaveTypeService = leaveTypeService;
        this.balanceService = balanceService;
        this.userService = userService;
        this.auditService = auditService;
        this.notificationService = notificationService;
    }

    @Transactional
    public LeaveRequestResponse submit(LeaveRequestCreateRequest request, long actorId) {
        // 1. Validate leave type
        String leaveTypeName = leaveTypeService.findNameById(request.getLeaveTypeId())
                .orElseThrow(() -> new ApiException(
                        HttpStatus.BAD_REQUEST,
                        ErrorCode.VALIDATION_FAILED,
                        "leaveTypeId " + request.getLeaveTypeId()
                                + " does not refer to an existing leave type."));

        // 2. Validate dates
        if (request.getEndDate().isBefore(request.getStartDate())) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    ErrorCode.VALIDATION_FAILED,
                    "endDate must be on or after startDate");
        }

        // 3. Compute working days
        int workingDays = WorkingDayCalculator.countWorkingDays(request.getStartDate(), request.getEndDate());
        if (workingDays == 0) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    ErrorCode.VALIDATION_FAILED,
                    "Range yields zero working days");
        }

        // 4. Determine approver: caller's manager, or self for HR with null manager
        Long managerId = userService.getManagerId(actorId);
        long approverId = managerId != null ? managerId : actorId;

        // 5. Insert request
        LeaveRequest leaveRequest = LeaveRequest.builder()
                .employeeId(actorId)
                .approverId(approverId)
                .leaveTypeId(request.getLeaveTypeId())
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .workingDays(workingDays)
                .notes(request.getNotes())
                .status(RequestStatus.PENDING)
                .submittedAt(Instant.now())
                .build();
        leaveRequest = leaveRequestRepository.save(leaveRequest);

        // 6. Reserve balance (checks availability, updates reserved_days, writes ledger row)
        balanceService.reserve(actorId, request.getLeaveTypeId(), workingDays, actorId, leaveRequest.getId());

        // 7. Audit
        auditService.recordEvent("LEAVE_REQUEST", leaveRequest.getId(), "LEAVE_REQUEST_SUBMITTED", actorId, null);

        NotificationMessage notification = new NotificationMessage(
                approverId,
                leaveRequest.getId(),
                NotificationKind.SUBMITTED,
                leaveTypeName,
                leaveRequest.getStartDate(),
                leaveRequest.getEndDate(),
                leaveRequest.getWorkingDays(),
                leaveRequest.getStatus().name());
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                notificationService.sendNotification(notification);
            }
        });

        return toResponse(leaveRequest);
    }

    private LeaveRequestResponse toResponse(LeaveRequest entity) {
        return LeaveRequestResponse.builder()
                .id(entity.getId())
                .employeeId(entity.getEmployeeId())
                .approverId(entity.getApproverId())
                .leaveTypeId(entity.getLeaveTypeId())
                .startDate(entity.getStartDate())
                .endDate(entity.getEndDate())
                .workingDays(entity.getWorkingDays())
                .notes(entity.getNotes())
                .status(entity.getStatus().name())
                .submittedAt(entity.getSubmittedAt())
                .decidedAt(entity.getDecidedAt())
                .decisionComment(entity.getDecisionComment())
                .build();
    }
}
