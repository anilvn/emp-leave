package com.company.leave.audit;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The only type in {@code audit} callable from outside the package (AD-2). Exposes
 * insert-only operations and a filtered request-history read, with no update or delete path.
 *
 * <p>Write methods take the actor id as an explicit parameter rather than deriving it from
 * {@code SecurityContextHolder} (AD-4): {@code audit} has no dependency on {@code security}
 * and must stay callable from contexts with no authenticated principal, such as a failed
 * login attempt.
 */
@Service
public class AuditService {

    private static final ObjectMapper JSON = new ObjectMapper();

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

    @Transactional(readOnly = true)
    public List<RequestHistoryEvent> findLeaveRequestEvents(long requestId) {
        return auditEventRepository.findRequestHistoryEvents(requestId)
                .stream()
                .map(event -> new RequestHistoryEvent(
                        event.getAction(),
                        event.getActorId(),
                        event.getDetailsJson(),
                        event.getCreatedAt()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<RequestCommentEvent> findLeaveRequestComments(long requestId) {
        return auditEventRepository.findRequestCommentEvents(requestId).stream()
                .map(event -> new RequestCommentEvent(
                        event.getId(),
                        event.getActorId(),
                        commentBody(event.getDetailsJson()),
                        event.getCreatedAt()))
                .toList();
    }

    private String commentBody(String detailsJson) {
        if (detailsJson == null || detailsJson.isBlank()) {
            return "Event details were not recorded.";
        }
        try {
            JsonNode details = JSON.readTree(detailsJson);
            if (details == null || !details.isObject()) {
                throw new IllegalStateException("Request comment audit details must be a JSON object.");
            }
            JsonNode comment = details.get("comment");
            if (comment == null || comment.isNull()) {
                return "Event details were not recorded.";
            }
            if (!comment.isTextual()) {
                throw new IllegalStateException("Request comment audit details must contain a text 'comment' value.");
            }
            return comment.textValue();
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Request comment audit details contain invalid JSON.", ex);
        }
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

    public record RequestHistoryEvent(
            String action, long actorId, String detailsJson, Instant createdAt) {}

    public record RequestCommentEvent(long id, long authorId, String body, Instant createdAt) {}
}
