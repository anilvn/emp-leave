package com.company.leave.auth;

import jakarta.validation.constraints.NotBlank;
import lombok.Value;

@Value
public class ChangePasswordRequest {
    @NotBlank
    String currentPassword;

    @NotBlank
    String newPassword;
}
