package com.company.leave.users;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;

@Schema(description = "The authenticated user returned by POST /auth/login")
@Value
@Builder
public class UserAccount {
    @Schema(description = "User id", example = "5")
    long id;

    @Schema(description = "Account username", example = "emp.jsmith")
    String username;

    @Schema(description = "Role governing access (FR21)", example = "EMPLOYEE")
    Role role;

    @Schema(description = "True while a temporary password must still be replaced (FR20)", example = "false")
    boolean mustChangePassword;
}
