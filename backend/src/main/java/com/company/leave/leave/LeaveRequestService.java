package com.company.leave.leave;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.company.leave.audit.AuditService;
import com.company.leave.balances.BalanceService;
import com.company.leave.common.ApiException;
import com.company.leave.common.ErrorCode;
import com.company.leave.common.WorkingDayCalculator;
import com.company.leave.notifications.NotificationKind;
import com.company.leave.notifications.NotificationMessage;
import com.company.leave.notifications.NotificationService;
import com.company.leave.security.AccessPolicy;
import com.company.leave.security.UserScope;
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
    private final AccessPolicy accessPolicy;

    LeaveRequestService(LeaveRequestRepository leaveRequestRepository,
                        LeaveTypeService leaveTypeService,
                        BalanceService balanceService,
                        UserService userService,
                        AuditService auditService,
                        NotificationService notificationService,
                        AccessPolicy accessPolicy) {
        this.leaveRequestRepository = leaveRequestRepository;
        this.leaveTypeService = leaveTypeService;
        this.balanceService = balanceService;
        this.userService = userService;
        this.auditService = auditService;
        this.notificationService = notificationService;
        this.accessPolicy = accessPolicy;
    }

    @Transactional(readOnly = true)
    public List<LeaveRequestResponse> list(RequestStatus status, Long employeeId) {
        UserScope scope = accessPolicy.currentScope();
        if (employeeId != null && !(scope instanceof UserScope.HrAll)) {
            Long employeeManagerId = userService.getManagerId(employeeId);
            accessPolicy.requireOwnerManagerOrHr(
                    employeeId, employeeManagerId == null ? employeeId : employeeManagerId);
        }
        List<LeaveRequest> requests = status == null && employeeId == null
                ? leaveRequestRepository.findByScope(scope)
                : leaveRequestRepository.findByScope(scope, status, employeeId);
        return requests.stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public LeaveRequestResponse get(long requestId) {
        LeaveRequest request = leaveRequestRepository.findById(requestId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        ErrorCode.NOT_FOUND,
                        "Leave request " + requestId + " does not exist."));
        accessPolicy.requireOwnerManagerOrHr(request.getEmployeeId(), request.getApproverId());
        return toResponse(request);
    }

    @Transactional
    public LeaveRequestResponse update(long requestId, LeaveRequestUpdateRequest update) {
        LeaveRequest request = leaveRequestRepository.findByIdForUpdate(requestId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        ErrorCode.NOT_FOUND,
                        "Leave request " + requestId + " does not exist."));
        accessPolicy.requireOwner(request.getEmployeeId());
        if (request.getStatus() != RequestStatus.PENDING) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    ErrorCode.INVALID_TRANSITION,
                    "Request " + requestId + " is " + request.getStatus() + " and can no longer be edited.");
        }
        if (!update.getLeaveTypeId().equals(request.getLeaveTypeId())) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    ErrorCode.LEAVE_TYPE_CHANGE_NOT_ALLOWED,
                    "Request " + requestId + " was submitted as leaveTypeId "
                            + request.getLeaveTypeId() + " and cannot be changed to leaveTypeId "
                            + update.getLeaveTypeId() + ".");
        }
        if (update.getEndDate().isBefore(update.getStartDate())) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    ErrorCode.VALIDATION_FAILED,
                    "endDate must be on or after startDate");
        }

        int workingDays = calculateWorkingDays(update.getStartDate(), update.getEndDate());
        if (workingDays == 0) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    ErrorCode.VALIDATION_FAILED,
                    "Range yields zero working days");
        }

        long actorId = accessPolicy.currentUserId();
        balanceService.adjustReservation(
                request.getEmployeeId(),
                request.getLeaveTypeId(),
                request.getWorkingDays(),
                workingDays,
                actorId,
                requestId);
        request.updatePendingDetails(update.getStartDate(), update.getEndDate(), workingDays, update.getNotes());
        leaveRequestRepository.save(request);
        auditService.recordEvent("LEAVE_REQUEST", requestId, "LEAVE_REQUEST_EDITED", actorId, null);
        return toResponse(request);
    }

    @Transactional
    public LeaveRequestResponse cancel(long requestId) {
        LeaveRequest request = leaveRequestRepository.findByIdForUpdate(requestId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        ErrorCode.NOT_FOUND,
                        "Leave request " + requestId + " does not exist."));
        accessPolicy.requireOwner(request.getEmployeeId());
        if (request.getStatus() != RequestStatus.PENDING) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    ErrorCode.INVALID_TRANSITION,
                    "Request " + requestId + " is " + request.getStatus()
                            + " and can no longer be cancelled.");
        }

        long actorId = accessPolicy.currentUserId();
        balanceService.releaseReservation(
                request.getEmployeeId(),
                request.getLeaveTypeId(),
                request.getWorkingDays(),
                actorId,
                requestId);
        request.cancelPending();
        leaveRequestRepository.save(request);
        auditService.recordEvent("LEAVE_REQUEST", requestId, "LEAVE_REQUEST_CANCELLED", actorId, null);
        return toResponse(request);
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
        int workingDays = calculateWorkingDays(request.getStartDate(), request.getEndDate());
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

    private int calculateWorkingDays(LocalDate startDate, LocalDate endDate) {
        try {
            return WorkingDayCalculator.countWorkingDays(startDate, endDate);
        } catch (ArithmeticException ex) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    ErrorCode.VALIDATION_FAILED,
                    "Date range exceeds the supported working-day limit.");
        }
    }
}
