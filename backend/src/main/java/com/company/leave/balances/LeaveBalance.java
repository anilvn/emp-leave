package com.company.leave.balances;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** A user's totals and active reservations for one leave type. */
@Entity
@Table(name = "leave_balances")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class LeaveBalance {

    @EmbeddedId
    private LeaveBalanceId id;

    @Column(name = "total_days", nullable = false)
    private int totalDays;

    @Column(name = "reserved_days", nullable = false)
    private int reservedDays;

    /**
     * Increases {@code reservedDays} by {@code delta}. The caller must have already
     * verified that the balance has enough available days; the database CHECK constraint
     * acts as defence in depth.
     */
    void reserveDays(int delta) {
        if (delta <= 0) {
            throw new IllegalArgumentException("delta must be positive");
        }
        this.reservedDays += delta;
    }
}
