package com.company.leave.balances;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Value;

@Schema(name = "BalanceAdjust", description = "Adjust a user's leave balance by a signed delta (FR29)")
@Value
class AdjustBalanceRequest {

    @NotNull
    @Schema(description = "Signed delta: positive adds days, negative removes days; must not be zero",
            example = "2")
    Integer changeDays;

    @NotBlank
    @Schema(description = "Required reason for the balance adjustment",
            example = "Carryover correction after manual review")
    String reason;
}
