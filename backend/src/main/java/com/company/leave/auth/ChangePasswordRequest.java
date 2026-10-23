package com.company.leave.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Value;

@Schema(description = "Password change submitted to POST /auth/change-password")
@Value
public class ChangePasswordRequest {
    @NotBlank
    @Schema(description = "The caller's current password", example = "Temp#2026",
            requiredMode = Schema.RequiredMode.REQUIRED)
    String currentPassword;

    @NotBlank
    @Schema(description = "New password: at least 8 characters, including both letters and numbers (NFR4)",
            example = "Passw0rd1", requiredMode = Schema.RequiredMode.REQUIRED)
    String newPassword;
}
