package com.company.leave.balances;

import java.util.List;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** Read-only balance persistence seam; balance writes belong to BalanceService. */
interface LeaveBalanceRepository extends Repository<LeaveBalance, LeaveBalanceId> {

    @Query("select balance from LeaveBalance balance where balance.id.userId = :userId")
    List<LeaveBalance> findForUser(@Param("userId") long userId);
}
