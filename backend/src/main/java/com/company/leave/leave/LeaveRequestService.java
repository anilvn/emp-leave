package com.company.leave.leave;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.security.access.AccessDeniedException;
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

    private static final ObjectMapper AUDIT_DETAILS_MAPPER = new ObjectMapper();

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

    /**
     * Returns pending requests assigned to the caller's approval scope. The repository applies
     * the scope and approver filter together so the dashboard never exposes another approver's
     * queue.
     */
    @Transactional(readOnly = true)
    public List<LeaveRequestResponse> findPendingApprovalsByScope(UserScope scope, long currentUserId) {
        return leaveRequestRepository.findPendingApprovalsByScope(scope, currentUserId).stream()
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

    @Transactional(readOnly = true)
    public List<HistoryEntry> history(long requestId) {
        LeaveRequest request = leaveRequestRepository.findById(requestId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        ErrorCode.NOT_FOUND,
                        "Leave request " + requestId + " does not exist."));
        accessPolicy.requireOwnerManagerOrHr(request.getEmployeeId(), request.getApproverId());
        List<AuditService.RequestHistoryEvent> events = auditService.findLeaveRequestEvents(requestId);
        Map<Long, String> actorUsernames = userService.getUsernamesByIds(
                events.stream().map(AuditService.RequestHistoryEvent::actorId).distinct().toList());
        return events.stream()
                .map(event -> toHistoryEntry(event, actorUsernames.get(event.actorId())))
                .toList();
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

        LocalDate previousStartDate = request.getStartDate();
        LocalDate previousEndDate = request.getEndDate();
        int previousWorkingDays = request.getWorkingDays();
        boolean notesChanged = !Objects.equals(request.getNotes(), update.getNotes());
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
        auditService.recordEvent(
                "LEAVE_REQUEST",
                requestId,
                "LEAVE_REQUEST_EDITED",
                actorId,
                encodeDetails(editDetail(
                        previousStartDate,
                        previousEndDate,
                        previousWorkingDays,
                        update.getStartDate(),
                        update.getEndDate(),
                        workingDays,
                        notesChanged)));
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
                requestId,
                "Cancelled leave request " + requestId);
        String detail = "Cancelled request for "
                + describeRange(request.getStartDate(), request.getEndDate(), request.getWorkingDays()) + ".";
        request.cancelPending();
        leaveRequestRepository.save(request);
        auditService.recordEvent(
                "LEAVE_REQUEST", requestId, "LEAVE_REQUEST_CANCELLED", actorId, encodeDetails(detail));
        return toResponse(request);
    }

    @Transactional
    public LeaveRequestResponse applyDecision(
            long requestId, LeaveRequestDecision decision, String comment, long approverId) {
        LeaveRequest request = leaveRequestRepository.findByIdForUpdate(requestId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        ErrorCode.NOT_FOUND,
                        "Leave request " + requestId + " does not exist."));
        if (request.getApproverId() != approverId) {
            throw new AccessDeniedException("Caller is not the assigned approver for request " + requestId);
        }
        String transitionVerb = decision == LeaveRequestDecision.REJECTED ? "rejected" : "approved";
        if (request.getStatus() != RequestStatus.PENDING) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    ErrorCode.INVALID_TRANSITION,
                    "Request " + requestId + " is " + request.getStatus()
                            + " and can no longer be " + transitionVerb + ".");
        }
        if (decision != LeaveRequestDecision.APPROVED && decision != LeaveRequestDecision.REJECTED) {
            throw new IllegalArgumentException("Unsupported decision: " + decision);
        }
        if (decision == LeaveRequestDecision.REJECTED && (comment == null || comment.isBlank())) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    ErrorCode.COMMENT_REQUIRED,
                    "A non-blank comment is required to reject a request.");
        }

        Instant decidedAt = Instant.now();
        if (decision == LeaveRequestDecision.APPROVED) {
            request.approve(approverId, decidedAt, comment);
        } else {
            request.reject(approverId, decidedAt, comment);
        }
        leaveRequestRepository.save(request);
        if (decision == LeaveRequestDecision.APPROVED) {
            balanceService.deductReservation(
                    request.getEmployeeId(),
                    request.getLeaveTypeId(),
                    request.getWorkingDays(),
                    approverId,
                    requestId);
        } else {
            balanceService.releaseReservation(
                    request.getEmployeeId(),
                    request.getLeaveTypeId(),
                    request.getWorkingDays(),
                    approverId,
                    requestId,
                    "Rejected leave request " + requestId);
        }
        String auditAction = decision == LeaveRequestDecision.APPROVED
                ? "LEAVE_REQUEST_APPROVED"
                : "LEAVE_REQUEST_REJECTED";
        auditService.recordEvent(
                "LEAVE_REQUEST",
                requestId,
                auditAction,
                approverId,
                encodeDetails(comment == null ? "Approved request." : comment));

        String leaveTypeName = leaveTypeService.findNameById(request.getLeaveTypeId())
                .orElseThrow(() -> new IllegalStateException(
                        "Leave type " + request.getLeaveTypeId() + " does not exist."));
        NotificationKind notificationKind = decision == LeaveRequestDecision.APPROVED
                ? NotificationKind.APPROVED
                : NotificationKind.REJECTED;
        NotificationMessage notification = new NotificationMessage(
                request.getEmployeeId(),
                requestId,
                notificationKind,
                leaveTypeName,
                request.getStartDate(),
                request.getEndDate(),
                request.getWorkingDays(),
                request.getStatus().name());
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                notificationService.sendNotification(notification);
            }
        });

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
        String detail = "Submitted " + leaveTypeName + ", "
                + describeRange(leaveRequest.getStartDate(), leaveRequest.getEndDate(), workingDays) + ".";
        auditService.recordEvent(
                "LEAVE_REQUEST", leaveRequest.getId(), "LEAVE_REQUEST_SUBMITTED", actorId, encodeDetails(detail));

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

    private HistoryEntry toHistoryEntry(AuditService.RequestHistoryEvent event, String actorUsername) {
        return HistoryEntry.builder()
                .type(historyEntryType(event.action()))
                .actorId(event.actorId())
                .actorUsername(actorUsername)
                .occurredAt(event.createdAt())
                .detail(decodeDetail(event.action(), event.detailsJson()))
                .build();
    }

    private HistoryEntryType historyEntryType(String action) {
        return switch (action) {
            case "LEAVE_REQUEST_SUBMITTED" -> HistoryEntryType.SUBMITTED;
            case "LEAVE_REQUEST_EDITED" -> HistoryEntryType.EDITED;
            case "LEAVE_REQUEST_CANCELLED" -> HistoryEntryType.CANCELLED;
            case "LEAVE_REQUEST_APPROVED" -> HistoryEntryType.APPROVED;
            case "LEAVE_REQUEST_REJECTED" -> HistoryEntryType.REJECTED;
            case "REQUEST_COMMENT_ADDED" -> HistoryEntryType.COMMENT;
            default -> throw new IllegalStateException("Unsupported request history audit action: " + action);
        };
    }

    private String decodeDetail(String action, String detailsJson) {
        if (detailsJson == null || detailsJson.isBlank()) {
            return "Event details were not recorded.";
        }
        try {
            String detailField = action.equals("REQUEST_COMMENT_ADDED") ? "comment" : "detail";
            JsonNode detail = AUDIT_DETAILS_MAPPER.readTree(detailsJson).get(detailField);
            if (detail == null || detail.isNull()) {
                return "Event details were not recorded.";
            }
            if (!detail.isTextual()) {
                throw new IllegalStateException("Request history audit details must contain a text 'detail' value.");
            }
            return detail.textValue();
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Request history audit details contain invalid JSON.", ex);
        }
    }

    private String encodeDetails(String detail) {
        try {
            return AUDIT_DETAILS_MAPPER.writeValueAsString(java.util.Map.of("detail", detail));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Could not encode request history audit details.", ex);
        }
    }

    private String editDetail(
            LocalDate previousStartDate,
            LocalDate previousEndDate,
            int previousWorkingDays,
            LocalDate startDate,
            LocalDate endDate,
            int workingDays,
            boolean notesChanged) {
        List<String> changes = new ArrayList<>();
        if (!previousStartDate.equals(startDate) || !previousEndDate.equals(endDate)) {
            changes.add("Changed dates from "
                    + describeRange(previousStartDate, previousEndDate, previousWorkingDays)
                    + " to " + describeRange(startDate, endDate, workingDays));
        }
        if (notesChanged) {
            changes.add("updated notes");
        }
        if (changes.isEmpty()) {
            changes.add("kept the date range at "
                    + describeRange(startDate, endDate, workingDays));
        }
        return "Edited request: " + String.join("; ", changes) + ".";
    }

    private String describeRange(LocalDate startDate, LocalDate endDate, int workingDays) {
        return startDate + " to " + endDate + " (" + workingDays + " working days)";
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
