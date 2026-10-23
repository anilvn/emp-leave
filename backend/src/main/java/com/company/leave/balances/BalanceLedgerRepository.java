package com.company.leave.balances;

import org.springframework.data.repository.Repository;

/** Insert-only persistence seam for balance ledger rows. */
interface BalanceLedgerRepository extends Repository<BalanceLedger, Long> {

    BalanceLedger save(BalanceLedger ledger);
}
