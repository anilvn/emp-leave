package com.company.leave.users;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Value;

@Schema(description = "New account submitted to POST /users (FR18, FR19)")
@Value
public class UserCreateRequest {
    @NotBlank
    @Size(max = 100)
    @Schema(description = "Unique username", example = "emp.blee", requiredMode = Schema.RequiredMode.REQUIRED)
    String username;

    @NotNull
    @Schema(description = "Role to assign", example = "EMPLOYEE", requiredMode = Schema.RequiredMode.REQUIRED)
    Role role;

    @Schema(description = "Manager id; required unless role is HR_ADMIN, and must reference a MANAGER "
            + "or HR_ADMIN user (FR19)", example = "4")
    Long managerId;

    @NotBlank
    @Schema(description = "HR-typed temporary password; BCrypt-hashed on save and never stored or "
            + "logged in plaintext (FR18)", example = "Temp#2026", requiredMode = Schema.RequiredMode.REQUIRED)
    String temporaryPassword;
}
