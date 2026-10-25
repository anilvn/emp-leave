package com.company.leave.approvals;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Value;

@Schema(name = "DecisionRequest", description = "Optional comment accompanying request approval")
@Value
public class ApprovalDecisionRequest {

    @Schema(description = "Optional comment for the employee", nullable = true, example = "Enjoy your trip!")
    String comment;
}
