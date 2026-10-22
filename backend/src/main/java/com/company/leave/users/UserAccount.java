package com.company.leave.users;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class UserAccount {
    long id;
    String username;
    Role role;
    boolean mustChangePassword;
}
