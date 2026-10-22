package com.company.leave.audit;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * One row per authorization decision, allowed or denied (FR43). Package-private: only
 * {@link AuditService} is callable from outside {@code audit} (AD-2).
 */
@Entity
@Table(name = "access_log")
class AccessLogEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Nullable: an unauthenticated attempt has no actor id to record (see Intent/Never).
    @Column(name = "actor_id")
    private Long actorId;

    @Column(nullable = false)
    private String resource;

    // Nullable: a collection-level check has no single resource id to record.
    @Column(name = "resource_id")
    private Long resourceId;

    @Column(nullable = false)
    private boolean allowed;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AccessLogEntry() {
        // JPA
    }

    AccessLogEntry(Long actorId, String resource, Long resourceId, boolean allowed, Instant createdAt) {
        this.actorId = actorId;
        this.resource = resource;
        this.resourceId = resourceId;
        this.allowed = allowed;
        this.createdAt = createdAt;
    }

    Long getId() {
        return id;
    }

    Long getActorId() {
        return actorId;
    }

    String getResource() {
        return resource;
    }

    Long getResourceId() {
        return resourceId;
    }

    boolean isAllowed() {
        return allowed;
    }

    Instant getCreatedAt() {
        return createdAt;
    }
}
