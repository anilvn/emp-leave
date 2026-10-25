package com.company.leave.approvals;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Value;

@Schema(name = "RejectDecisionRequest", description = "Required non-blank reason accompanying request rejection")
@Value
public class RejectDecisionRequest {

    @Schema(
            description = "Non-blank reason for rejecting the request",
            requiredMode = Schema.RequiredMode.REQUIRED,
            minLength = 1,
            pattern = ".*\\S.*",
            example = "Please resubmit after the month-end close.")
    String comment;
}
