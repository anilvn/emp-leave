package com.company.leave.auth;

import lombok.Value;

@Value
public class LoginRequest {
    String username;
    String password;
}
