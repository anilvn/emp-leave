package com.company.leave.balances;

import java.util.List;

import org.springframework.data.repository.Repository;

/** Persistence seam for balance ledger rows — insert only and ledger reads by user/leave type. */
interface BalanceLedgerRepository extends Repository<BalanceLedger, Long> {

    BalanceLedger save(BalanceLedger ledger);

    List<BalanceLedger> findByUserIdAndLeaveTypeIdOrderByCreatedAtAsc(long userId, long leaveTypeId);
}
