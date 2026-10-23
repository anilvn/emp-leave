package com.company.leave.users;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class TemporaryPasswordResponse {
    String temporaryPassword;
}
