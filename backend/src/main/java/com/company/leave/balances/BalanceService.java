package com.company.leave.balances;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.company.leave.audit.AuditService;
import com.company.leave.common.ApiException;
import com.company.leave.common.ErrorCode;
import com.company.leave.leave.LeaveTypeResponse;
import com.company.leave.leave.LeaveTypeService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Reads balances, fills missing pairs with zero days, and manages reservations. */
@Service
public class BalanceService {

    private final LeaveBalanceRepository leaveBalanceRepository;
    private final BalanceLedgerRepository balanceLedgerRepository;
    private final LeaveTypeService leaveTypeService;
    private final AuditService auditService;

    BalanceService(LeaveBalanceRepository leaveBalanceRepository,
                   BalanceLedgerRepository balanceLedgerRepository,
                   LeaveTypeService leaveTypeService,
                   AuditService auditService) {
        this.leaveBalanceRepository = leaveBalanceRepository;
        this.balanceLedgerRepository = balanceLedgerRepository;
        this.leaveTypeService = leaveTypeService;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<BalanceResponse> getBalancesForUser(long userId) {
        Map<Long, LeaveBalance> balancesByType = leaveBalanceRepository.findForUser(userId).stream()
                .collect(Collectors.toMap(balance -> balance.getId().getLeaveTypeId(), Function.identity()));

        return leaveTypeService.listLeaveTypes().stream()
                .map(leaveType -> toBalanceResponse(userId, leaveType, balancesByType.get(leaveType.getId())))
                .toList();
    }

    /**
     * Reserves {@code workingDays} against the user's balance for the given leave type.
     * Throws {@link ApiException} with {@link ErrorCode#INSUFFICIENT_BALANCE} when available
     * days are insufficient. Updates {@code reserved_days} and writes a RESERVE ledger row.
     *
     * <p>Must be called within an existing transaction so that the request insert, balance
     * update, ledger row, and audit event all commit or roll back together.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void reserve(long userId, long leaveTypeId, int workingDays, long actorId, long requestId) {
        LeaveBalanceId balanceId = new LeaveBalanceId(userId, leaveTypeId);
        LeaveBalance balance = leaveBalanceRepository.findById(balanceId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.CONFLICT, ErrorCode.INSUFFICIENT_BALANCE,
                        "No balance row exists for userId=" + userId + " leaveTypeId=" + leaveTypeId));

        int available = balance.getAvailableDays();
        if (workingDays > available) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    ErrorCode.INSUFFICIENT_BALANCE,
                    "Insufficient balance: requested=" + workingDays
                            + ", available=" + available
                            + ", total=" + balance.getTotalDays()
                            + ", reserved=" + balance.getReservedDays());
        }

        balance.reserveDays(workingDays);
        leaveBalanceRepository.save(balance);

        balanceLedgerRepository.save(new BalanceLedger(
                userId, leaveTypeId, workingDays, LedgerKind.RESERVE,
                "Leave request " + requestId, actorId, requestId, Instant.now()));
    }

    /**
     * Replaces a request's held days without exposing them as available to other requests.
     * Must be called inside the request's transaction.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void adjustReservation(long userId, long leaveTypeId, int currentWorkingDays,
                                  int newWorkingDays, long actorId, long requestId) {
        if (currentWorkingDays == newWorkingDays) {
            return;
        }

        LeaveBalanceId balanceId = new LeaveBalanceId(userId, leaveTypeId);
        LeaveBalance balance = leaveBalanceRepository.findById(balanceId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.CONFLICT, ErrorCode.INSUFFICIENT_BALANCE,
                        "No balance row exists for userId=" + userId + " leaveTypeId=" + leaveTypeId));

        int availableToRequest = balance.getTotalDays() - balance.getReservedDays() + currentWorkingDays;
        if (newWorkingDays > availableToRequest) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    ErrorCode.INSUFFICIENT_BALANCE,
                    "Edit would reserve " + newWorkingDays + " working days but only "
                            + availableToRequest + " are available to this request.");
        }

        balance.adjustReservation(newWorkingDays - currentWorkingDays);
        leaveBalanceRepository.save(balance);

        Instant now = Instant.now();
        String reason = "Edited leave request " + requestId;
        balanceLedgerRepository.save(new BalanceLedger(
                userId, leaveTypeId, -currentWorkingDays, LedgerKind.RELEASE,
                reason + ": released previous reservation", actorId, requestId, now));
        balanceLedgerRepository.save(new BalanceLedger(
                userId, leaveTypeId, newWorkingDays, LedgerKind.RESERVE,
                reason + ": reserved updated amount", actorId, requestId, now));
    }

    /**
     * Releases a request's held days and records the matching ledger entry.
     * Must be called inside the request's transaction.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void releaseReservation(long userId, long leaveTypeId, int workingDays,
                                   long actorId, long requestId, String reason) {
        if (workingDays <= 0) {
            throw new IllegalArgumentException("workingDays must be positive");
        }

        LeaveBalanceId balanceId = new LeaveBalanceId(userId, leaveTypeId);
        LeaveBalance balance = leaveBalanceRepository.findById(balanceId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.CONFLICT, ErrorCode.INSUFFICIENT_BALANCE,
                        "No balance row exists for userId=" + userId + " leaveTypeId=" + leaveTypeId));

        if (balance.getReservedDays() < workingDays) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    ErrorCode.INSUFFICIENT_BALANCE,
                    "Cannot release " + workingDays + " working days for request " + requestId
                            + " from a reservation of " + balance.getReservedDays() + " days.");
        }

        balance.adjustReservation(-workingDays);
        leaveBalanceRepository.save(balance);

        balanceLedgerRepository.save(new BalanceLedger(
                userId, leaveTypeId, -workingDays, LedgerKind.RELEASE,
                reason, actorId, requestId, Instant.now()));
    }

    /**
     * Converts a request's reservation into deducted entitlement and records the matching
     * ledger entry. Must be called inside the request's transaction.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void deductReservation(long userId, long leaveTypeId, int workingDays,
                                  long actorId, long requestId) {
        if (workingDays <= 0) {
            throw new IllegalArgumentException("workingDays must be positive");
        }

        LeaveBalanceId balanceId = new LeaveBalanceId(userId, leaveTypeId);
        LeaveBalance balance = leaveBalanceRepository.findById(balanceId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.CONFLICT, ErrorCode.INSUFFICIENT_BALANCE,
                        "No balance row exists for userId=" + userId + " leaveTypeId=" + leaveTypeId));

        if (balance.getReservedDays() < workingDays || balance.getTotalDays() < workingDays) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    ErrorCode.INSUFFICIENT_BALANCE,
                    "Cannot deduct " + workingDays + " working days for request " + requestId
                            + " from total=" + balance.getTotalDays()
                            + ", reserved=" + balance.getReservedDays() + ".");
        }

        balance.deductReservedDays(workingDays);
        leaveBalanceRepository.save(balance);
        balanceLedgerRepository.save(new BalanceLedger(
                userId,
                leaveTypeId,
                -workingDays,
                LedgerKind.DEDUCT,
                "Approved leave request " + requestId,
                actorId,
                requestId,
                Instant.now()));
    }

    /**
     * Sets {@code total_days} for the given user and leave type, writing a {@code SET}
     * ledger row and an audit event in the same transaction (FR28, FR30, FR42, AD-4, AD-5).
     */
    @Transactional
    public BalanceResponse setBalance(long userId, long leaveTypeId, int newTotalDays,
                                      String reason, long actorId) {
        LeaveBalanceId balanceId = new LeaveBalanceId(userId, leaveTypeId);
        LeaveBalance balance = leaveBalanceRepository.findById(balanceId)
                .orElse(new LeaveBalance(balanceId, 0));

        if (newTotalDays < balance.getReservedDays()) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    ErrorCode.INSUFFICIENT_BALANCE,
                    "Cannot set total to " + newTotalDays + " days; "
                            + balance.getReservedDays()
                            + " days are already reserved for pending requests.");
        }

        int oldTotalDays = balance.getTotalDays();
        balance.setTotalDays(newTotalDays);
        leaveBalanceRepository.save(balance);

        balanceLedgerRepository.save(new BalanceLedger(
                userId, leaveTypeId, newTotalDays - oldTotalDays, LedgerKind.SET,
                reason, actorId, null, Instant.now()));

        auditService.recordEvent(
                "LEAVE_BALANCE", userId, "LEAVE_BALANCE_SET", actorId,
                "{\"totalDays\":" + newTotalDays + ",\"leaveTypeId\":" + leaveTypeId + "}");

        return BalanceResponse.builder()
                .userId(userId)
                .leaveTypeId(leaveTypeId)
                .totalDays(balance.getTotalDays())
                .reservedDays(balance.getReservedDays())
                .availableDays(balance.getAvailableDays())
                .build();
    }

    /**
     * Adjusts {@code total_days} for the given user and leave type by a signed delta, writing an
     * {@code ADJUST} ledger row and an audit event in the same transaction (FR29, FR30, FR31).
     */
    @Transactional
    public BalanceResponse adjustBalance(long userId, long leaveTypeId, int changeDays,
                                         String reason, long actorId) {
        if (changeDays == 0) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    ErrorCode.VALIDATION_FAILED,
                    "changeDays must not be zero");
        }

        LeaveBalanceId balanceId = new LeaveBalanceId(userId, leaveTypeId);
        LeaveBalance balance = leaveBalanceRepository.findById(balanceId)
                .orElse(new LeaveBalance(balanceId, 0));

        int newTotal = balance.getTotalDays() + changeDays;
        if (newTotal < 0 || newTotal < balance.getReservedDays()) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    ErrorCode.INSUFFICIENT_BALANCE,
                    "Adjustment of " + changeDays + " days would take the balance below zero or "
                            + "below reserved days (" + balance.getReservedDays() + " reserved, "
                            + "current total " + balance.getTotalDays() + ").");
        }

        balance.setTotalDays(newTotal);
        leaveBalanceRepository.save(balance);

        balanceLedgerRepository.save(new BalanceLedger(
                userId, leaveTypeId, changeDays, LedgerKind.ADJUST,
                reason, actorId, null, Instant.now()));

        auditService.recordEvent(
                "LEAVE_BALANCE", userId, "LEAVE_BALANCE_ADJUSTED", actorId,
                "{\"changeDays\":" + changeDays + ",\"leaveTypeId\":" + leaveTypeId + "}");

        return BalanceResponse.builder()
                .userId(userId)
                .leaveTypeId(leaveTypeId)
                .totalDays(balance.getTotalDays())
                .reservedDays(balance.getReservedDays())
                .availableDays(balance.getAvailableDays())
                .build();
    }

    @Transactional(readOnly = true)
    public List<BalanceLedgerEntryResponse> getLedger(long userId, long leaveTypeId) {
        return balanceLedgerRepository
                .findByUserIdAndLeaveTypeIdOrderByCreatedAtAsc(userId, leaveTypeId)
                .stream()
                .map(e -> BalanceLedgerEntryResponse.builder()
                        .id(e.getId())
                        .userId(e.getUserId())
                        .leaveTypeId(e.getLeaveTypeId())
                        .changeDays(e.getChangeDays())
                        .kind(e.getKind())
                        .reason(e.getReason())
                        .actorId(e.getActorId())
                        .requestId(e.getRequestId())
                        .createdAt(e.getCreatedAt())
                        .build())
                .toList();
    }

    @Transactional(readOnly = true)
    public List<BalanceResponse> getAllBalances(Long userId) {
        List<LeaveBalance> rows = userId != null
                ? leaveBalanceRepository.findForUser(userId)
                : leaveBalanceRepository.findAll();
        return rows.stream()
                .map(b -> BalanceResponse.builder()
                        .userId(b.getId().getUserId())
                        .leaveTypeId(b.getId().getLeaveTypeId())
                        .totalDays(b.getTotalDays())
                        .reservedDays(b.getReservedDays())
                        .availableDays(b.getAvailableDays())
                        .build())
                .toList();
    }

    private BalanceResponse toBalanceResponse(long userId, LeaveTypeResponse leaveType, LeaveBalance balance) {
        int totalDays = balance == null ? 0 : balance.getTotalDays();
        int reservedDays = balance == null ? 0 : balance.getReservedDays();
        int availableDays = balance == null ? 0 : balance.getAvailableDays();
        return BalanceResponse.builder()
                .userId(userId)
                .leaveTypeId(leaveType.getId())
                .totalDays(totalDays)
                .reservedDays(reservedDays)
                .availableDays(availableDays)
                .build();
    }
}
