package com.company.leave.users;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Value;

@Value
public class UserCreateRequest {
    @NotBlank
    @Size(max = 100)
    String username;

    @NotNull
    Role role;

    Long managerId;

    @NotBlank
    String temporaryPassword;
}
