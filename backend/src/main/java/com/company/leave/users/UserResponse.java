package com.company.leave.users;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;

@Schema(description = "A user account")
@Value
@Builder
public class UserResponse {
    @Schema(description = "User id", example = "13")
    long id;

    @Schema(description = "Account username", example = "emp.blee")
    String username;

    @Schema(description = "Assigned role (FR21)", example = "EMPLOYEE")
    Role role;

    @Schema(description = "Manager id, or null for a top-level HR_ADMIN", example = "4")
    Long managerId;

    @Schema(description = "True while a temporary password must still be replaced (FR20)", example = "true")
    boolean mustChangePassword;

    @Schema(description = "Whether the account is active", example = "true")
    boolean active;
}
