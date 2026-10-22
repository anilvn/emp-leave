package com.company.leave.audit;

import org.springframework.data.repository.Repository;

/**
 * Extends the plain {@link Repository} marker interface, not {@code CrudRepository}/
 * {@code JpaRepository}, so no {@code delete*} method is ever inherited (AD-6). The
 * only declared method is {@code save}; there is no update or delete path.
 */
interface AccessLogRepository extends Repository<AccessLogEntry, Long> {

    AccessLogEntry save(AccessLogEntry accessLogEntry);
}
