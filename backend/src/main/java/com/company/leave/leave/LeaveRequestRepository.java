package com.company.leave.leave;

import java.util.List;
import java.util.Optional;

import com.company.leave.security.UserScope;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** Persistence seam for leave requests. Package-private (AD-2). */
interface LeaveRequestRepository extends Repository<LeaveRequest, Long> {

    LeaveRequest save(LeaveRequest request);

    Optional<LeaveRequest> findById(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select request from LeaveRequest request where request.id = :id")
    Optional<LeaveRequest> findByIdForUpdate(@Param("id") Long id);

    @Query("""
            select request from LeaveRequest request
            where request.employeeId = :employeeId
              and (:status is null or request.status = :status)
            order by request.submittedAt desc, request.id desc
            """)
    List<LeaveRequest> findForEmployee(
            @Param("employeeId") long employeeId, @Param("status") RequestStatus status);

    @Query("""
            select request from LeaveRequest request
            where (request.employeeId = :managerId or request.approverId = :managerId)
              and (:status is null or request.status = :status)
            order by request.submittedAt desc, request.id desc
            """)
    List<LeaveRequest> findForManager(
            @Param("managerId") long managerId, @Param("status") RequestStatus status);

    @Query("""
            select request from LeaveRequest request
            where (:status is null or request.status = :status)
            order by request.submittedAt desc, request.id desc
            """)
    List<LeaveRequest> findAllByStatus(@Param("status") RequestStatus status);

    @Query("""
            select request from LeaveRequest request
            where request.status = :status
              and request.approverId = :approverId
            order by request.submittedAt asc, request.id asc
            """)
    List<LeaveRequest> findPendingAssignedTo(
            @Param("approverId") long approverId, @Param("status") RequestStatus status);

    default List<LeaveRequest> findByScope(UserScope scope) {
        return findByScope(scope, null, null);
    }

    default List<LeaveRequest> findPendingApprovalsByScope(UserScope scope, long currentUserId) {
        if (scope instanceof UserScope.ManagerTeam managerTeam) {
            return findPendingAssignedTo(managerTeam.managerId(), RequestStatus.PENDING);
        }
        if (scope instanceof UserScope.HrAll) {
            return findPendingAssignedTo(currentUserId, RequestStatus.PENDING);
        }
        throw new IllegalArgumentException("Employee scope cannot read pending approvals: " + scope);
    }

    default List<LeaveRequest> findByScope(UserScope scope, RequestStatus status, Long employeeId) {
        if (scope instanceof UserScope.EmployeeOwn employeeOwn) {
            return findForEmployee(employeeOwn.userId(), status);
        }
        if (scope instanceof UserScope.ManagerTeam managerTeam) {
            return employeeId == null
                    ? findForManager(managerTeam.managerId(), status)
                    : findForEmployee(employeeId, status);
        }
        if (scope instanceof UserScope.HrAll) {
            return employeeId == null ? findAllByStatus(status) : findForEmployee(employeeId, status);
        }
        throw new IllegalArgumentException("Unsupported request scope: " + scope);
    }
}
