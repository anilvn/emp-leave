package com.company.leave.users;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;

@Schema(description = "A user selectable as a manager in the create-account form (FR19)")
@Value
@Builder
public class ManagerOptionResponse {
    @Schema(description = "User id", example = "3")
    long id;

    @Schema(description = "Account username", example = "mgr.chen")
    String username;

    @Schema(description = "Role (MANAGER or HR_ADMIN)", example = "MANAGER")
    Role role;
}
