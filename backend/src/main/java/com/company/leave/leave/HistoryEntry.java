package com.company.leave.leave;

import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;

@Schema(name = "HistoryEntry", description = "One event in a leave request's chronological history")
@Value
@Builder
public class HistoryEntry {

    HistoryEntryType type;

    long actorId;

    String actorUsername;

    Instant occurredAt;

    String detail;
}
