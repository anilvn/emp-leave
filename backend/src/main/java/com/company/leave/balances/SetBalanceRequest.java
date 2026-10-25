package com.company.leave.balances;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Value;

@Schema(name = "BalanceSet", description = "Set a user's leave balance total days (FR28)")
@Value
class SetBalanceRequest {

    @NotNull
    @Min(0)
    @Schema(description = "New total days, must be 0 or more", example = "20")
    Integer totalDays;

    @NotBlank
    @Schema(description = "Required reason for the balance change",
            example = "Annual Vacation allotment for 2027")
    String reason;
}
