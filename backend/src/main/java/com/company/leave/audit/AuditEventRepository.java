package com.company.leave.audit;

import java.util.List;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * Extends the plain {@link Repository} marker interface, not {@code CrudRepository}/
 * {@code JpaRepository}, so no {@code delete*} method is ever inherited (AD-6). The
 * Only event insertion and a filtered history read are exposed; there is no update or delete path.
 */
interface AuditEventRepository extends Repository<AuditEvent, Long> {

    AuditEvent save(AuditEvent auditEvent);

    @Query(value = """
            select *
            from audit_events
            where (
                (entity = 'LEAVE_REQUEST'
                    and entity_id = :requestId
                    and action in (
                        'LEAVE_REQUEST_SUBMITTED',
                        'LEAVE_REQUEST_EDITED',
                        'LEAVE_REQUEST_CANCELLED',
                        'LEAVE_REQUEST_APPROVED',
                        'LEAVE_REQUEST_REJECTED'
                    ))
                    or (entity = 'REQUEST_COMMENT'
                        and action = 'REQUEST_COMMENT_ADDED'
                        and details_json ->> 'requestId' = cast(:requestId as text))
            )
            order by created_at asc, id asc
            """, nativeQuery = true)
    List<AuditEvent> findRequestHistoryEvents(@Param("requestId") long requestId);

    @Query(value = """
            select *
            from audit_events
            where entity = 'REQUEST_COMMENT'
              and action = 'REQUEST_COMMENT_ADDED'
              and details_json ->> 'requestId' = cast(:requestId as text)
            order by created_at asc, id asc
            """, nativeQuery = true)
    List<AuditEvent> findRequestCommentEvents(@Param("requestId") long requestId);
}
