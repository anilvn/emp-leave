package com.company.leave.balances;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** One row per balance change — the immutable ledger backing leave_balances. */
@Entity
@Table(name = "balance_ledger")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class BalanceLedger {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private long userId;

    @Column(name = "leave_type_id", nullable = false)
    private long leaveTypeId;

    @Column(name = "change_days", nullable = false)
    private int changeDays;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LedgerKind kind;

    private String reason;

    @Column(name = "actor_id", nullable = false)
    private long actorId;

    @Column(name = "request_id")
    private Long requestId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    BalanceLedger(long userId, long leaveTypeId, int changeDays, LedgerKind kind,
                  String reason, long actorId, Long requestId, Instant createdAt) {
        this.userId = userId;
        this.leaveTypeId = leaveTypeId;
        this.changeDays = changeDays;
        this.kind = kind;
        this.reason = reason;
        this.actorId = actorId;
        this.requestId = requestId;
        this.createdAt = createdAt;
    }
}
