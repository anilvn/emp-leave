package com.company.leave.balances;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.company.leave.leave.LeaveTypeResponse;
import com.company.leave.leave.LeaveTypeService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Reads balances and fills any missing user/type pair with zero days. */
@Service
public class BalanceService {

    private final LeaveBalanceRepository leaveBalanceRepository;
    private final LeaveTypeService leaveTypeService;

    BalanceService(LeaveBalanceRepository leaveBalanceRepository, LeaveTypeService leaveTypeService) {
        this.leaveBalanceRepository = leaveBalanceRepository;
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
