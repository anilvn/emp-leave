package com.company.leave.users;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ManagerOptionResponse {
    long id;
    String username;
    Role role;
}
