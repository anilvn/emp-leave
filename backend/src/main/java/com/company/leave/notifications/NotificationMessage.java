package com.company.leave.notifications;

import java.time.LocalDate;

public record NotificationMessage(
        long userId,
        long requestId,
        NotificationKind kind,
        String leaveType,
        LocalDate startDate,
        LocalDate endDate,
        int workingDays,
        String status) {}
