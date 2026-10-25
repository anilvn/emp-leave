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

    void adjustReservation(int delta) {
        int updatedReservedDays = this.reservedDays + delta;
        if (updatedReservedDays < 0 || updatedReservedDays > totalDays) {
            throw new IllegalArgumentException("adjusted reservation must remain within the balance");
        }
        this.reservedDays = updatedReservedDays;
    }

    void deductReservedDays(int days) {
        if (days <= 0 || days > reservedDays || days > totalDays) {
            throw new IllegalArgumentException("deduction must not exceed reserved or total days");
        }
        this.reservedDays -= days;
        this.totalDays -= days;
    }

    /** Creates a new balance row with zero reserved days. Used by HR set when no row exists yet. */
    LeaveBalance(LeaveBalanceId id, int totalDays) {
        this.id = id;
        this.totalDays = totalDays;
        this.reservedDays = 0;
    }

    /** Sets total days; the caller must have verified newTotal >= reservedDays. */
    void setTotalDays(int newTotal) {
        this.totalDays = newTotal;
    }
}
