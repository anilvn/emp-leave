package com.company.leave.balances;

/** Why a {@code balance_ledger} row was written. */
enum LedgerKind {
    SET,
    ADJUST,
    RESERVE,
    RELEASE,
    DEDUCT
}
