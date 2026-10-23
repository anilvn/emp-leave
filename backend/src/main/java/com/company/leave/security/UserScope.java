package com.company.leave.security;

public sealed interface UserScope
        permits UserScope.EmployeeOwn, UserScope.ManagerTeam, UserScope.HrAll {

    record EmployeeOwn(long userId) implements UserScope {}

    record ManagerTeam(long managerId) implements UserScope {}

    record HrAll() implements UserScope {}
}
