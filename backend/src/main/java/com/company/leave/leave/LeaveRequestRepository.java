package com.company.leave.leave;

import org.springframework.data.repository.Repository;

/** Persistence seam for leave requests. Package-private (AD-2). */
interface LeaveRequestRepository extends Repository<LeaveRequest, Long> {

    LeaveRequest save(LeaveRequest request);
}
