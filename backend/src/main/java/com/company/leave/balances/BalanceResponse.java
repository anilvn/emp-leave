package com.company.leave.balances;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;

@Schema(name = "Balance", description = "One leave balance for a user and leave type")
@Value
@Builder
public class BalanceResponse {

    @Schema(description = "User id", example = "5")
    long userId;

    @Schema(description = "Leave type id", example = "1")
    long leaveTypeId;

    @Schema(description = "Days allocated to the leave type", example = "20")
    int totalDays;

    @Schema(description = "Days currently reserved", example = "4")
    int reservedDays;

    @Schema(description = "Days available, calculated as totalDays minus reservedDays", example = "16")
    int availableDays;
}
