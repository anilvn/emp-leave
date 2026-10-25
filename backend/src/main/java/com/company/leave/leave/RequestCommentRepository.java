package com.company.leave.leave;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence seam for request comments. Package-private (AD-2). */
interface RequestCommentRepository extends JpaRepository<RequestComment, Long> {

    List<RequestComment> findByRequestIdOrderByCreatedAtAscIdAsc(long requestId);
}
