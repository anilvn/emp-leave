package com.company.leave.audit;

import java.time.Instant;

import org.springframework.stereotype.Service;

/**
 * The only type in {@code audit} callable from outside the package (AD-2). Exposes two
 * insert-only operations and nothing else — no update, no delete, no read — because the
 * immutability guarantee (AD-6) only holds if the write path never offers a way around it.
 *
 * <p>Both methods take the actor id as an explicit parameter rather than deriving it from
 * {@code SecurityContextHolder} (AD-4): {@code audit} has no dependency on {@code security}
 * and must stay callable from contexts with no authenticated principal, such as a failed
 * login attempt.
 */
@Service
public class AuditService {

    private final AuditEventRepository auditEventRepository;
    private final AccessLogRepository accessLogRepository;

    AuditService(AuditEventRepository auditEventRepository, AccessLogRepository accessLogRepository) {
        this.auditEventRepository = auditEventRepository;
        this.accessLogRepository = accessLogRepository;
    }

    /**
     * Records that {@code actorId} performed {@code action} on {@code entity}/{@code entityId}.
     * {@code detailsJson} is an arbitrary JSON payload, stored as-is in the {@code jsonb} column.
     */
    public void recordEvent(String entity, Long entityId, String action, long actorId, String detailsJson) {
        auditEventRepository.save(new AuditEvent(entity, entityId, action, actorId, detailsJson, Instant.now()));
    }

    /**
     * Records an authorization decision, allowed or denied, against {@code resource}/
     * {@code resourceId}. {@code actorId}/{@code resourceId} may be {@code null}: an
     * unauthenticated attempt has no actor id, and a collection-level check has no single
     * resource id.
     */
    public void recordAccess(Long actorId, String resource, Long resourceId, boolean allowed) {
        accessLogRepository.save(new AccessLogEntry(actorId, resource, resourceId, allowed, Instant.now()));
    }
}
