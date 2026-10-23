package com.company.leave.notifications;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "notifications")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private long userId;

    @Column(name = "request_id", nullable = false)
    private long requestId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationKind kind;

    @Column(nullable = false, length = 500)
    private String subject;

    @Column(name = "sent_at", nullable = false)
    private Instant sentAt;

    @Builder
    Notification(long userId, long requestId, NotificationKind kind, String subject, Instant sentAt) {
        this.userId = userId;
        this.requestId = requestId;
        this.kind = kind;
        this.subject = subject;
        this.sentAt = sentAt;
    }
}
