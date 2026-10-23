package com.company.leave.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Value;

@Schema(description = "Credentials submitted to POST /auth/login")
@Value
public class LoginRequest {
    @NotBlank
    @Schema(description = "Account username", example = "emp.jsmith", requiredMode = Schema.RequiredMode.REQUIRED)
    String username;

    @NotBlank
    @Schema(description = "Account password", example = "Passw0rd1", requiredMode = Schema.RequiredMode.REQUIRED)
    String password;
}
