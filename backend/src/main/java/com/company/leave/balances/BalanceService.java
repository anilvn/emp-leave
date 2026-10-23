package com.company.leave.balances;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

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

    BalanceService(LeaveBalanceRepository leaveBalanceRepository,
                   BalanceLedgerRepository balanceLedgerRepository,
                   LeaveTypeService leaveTypeService) {
        this.leaveBalanceRepository = leaveBalanceRepository;
        this.balanceLedgerRepository = balanceLedgerRepository;
        this.leaveTypeService = leaveTypeService;
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

        int available = balance.getTotalDays() - balance.getReservedDays();
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

    private BalanceResponse toBalanceResponse(long userId, LeaveTypeResponse leaveType, LeaveBalance balance) {
        int totalDays = balance == null ? 0 : balance.getTotalDays();
        int reservedDays = balance == null ? 0 : balance.getReservedDays();
        return BalanceResponse.builder()
                .userId(userId)
                .leaveTypeId(leaveType.getId())
                .totalDays(totalDays)
                .reservedDays(reservedDays)
                .availableDays(totalDays - reservedDays)
                .build();
    }
}
