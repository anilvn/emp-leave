package com.company.leave.audit;

import java.time.Instant;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * One row per recorded action (FR43). Package-private: only {@link AuditService} is
 * callable from outside {@code audit} (AD-2); callers never touch this entity directly.
 */
@Entity
@Table(name = "audit_events")
class AuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String entity;

    @Column(name = "entity_id")
    private Long entityId;

    @Column(nullable = false)
    private String action;

    @Column(name = "actor_id", nullable = false)
    private long actorId;

    @Column(name = "details_json")
    @JdbcTypeCode(SqlTypes.JSON)
    private String detailsJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AuditEvent() {
        // JPA
    }

    AuditEvent(String entity, Long entityId, String action, long actorId, String detailsJson, Instant createdAt) {
        this.entity = entity;
        this.entityId = entityId;
        this.action = action;
        this.actorId = actorId;
        this.detailsJson = detailsJson;
        this.createdAt = createdAt;
    }

    Long getId() {
        return id;
    }

    String getEntity() {
        return entity;
    }

    Long getEntityId() {
        return entityId;
    }

    String getAction() {
        return action;
    }

    long getActorId() {
        return actorId;
    }

    String getDetailsJson() {
        return detailsJson;
    }

    Instant getCreatedAt() {
        return createdAt;
    }
}
