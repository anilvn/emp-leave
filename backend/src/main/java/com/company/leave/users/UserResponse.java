package com.company.leave.users;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class UserResponse {
    long id;
    String username;
    Role role;
    Long managerId;
    boolean mustChangePassword;
    boolean active;
}
