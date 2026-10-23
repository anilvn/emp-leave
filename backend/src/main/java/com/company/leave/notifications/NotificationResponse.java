package com.company.leave.notifications;

import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "A notification recorded for the authenticated user")
public record NotificationResponse(
        long id,
        long userId,
        long requestId,
        NotificationKind kind,
        String subject,
        Instant sentAt) {}
