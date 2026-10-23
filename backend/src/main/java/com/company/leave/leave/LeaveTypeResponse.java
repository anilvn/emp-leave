package com.company.leave.leave;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;

@Schema(description = "A supported leave type")
@Value
@Builder
public class LeaveTypeResponse {

    @Schema(description = "Leave type id", example = "1")
    long id;

    @Schema(description = "Human-readable leave type name", example = "Vacation")
    String name;
}
