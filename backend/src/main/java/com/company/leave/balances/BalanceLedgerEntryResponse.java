package com.company.leave.balances;

import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;

@Schema(name = "BalanceLedgerEntry", description = "balance_ledger table (FR30)")
@Value
@Builder
public class BalanceLedgerEntryResponse {

    @Schema(description = "Row id", example = "480")
    long id;

    @Schema(description = "User id", example = "5")
    long userId;

    @Schema(description = "Leave type id", example = "1")
    long leaveTypeId;

    @Schema(description = "Signed change in days (positive = added, negative = removed)", example = "4")
    int changeDays;

    @Schema(description = "Why this ledger row was written", example = "ADJUST")
    LedgerKind kind;

    @Schema(description = "Human-readable reason", example = "Carryover correction after manual review")
    String reason;

    @Schema(description = "User id of the actor who caused this change", example = "1")
    long actorId;

    @Schema(description = "Leave request id that caused this row, null for HR-initiated changes",
            example = "101", nullable = true)
    Long requestId;

    @Schema(description = "When this row was written", example = "2026-10-22T09:15:00Z")
    Instant createdAt;
}
