package com.company.leave.leave;

import java.time.Instant;
import java.time.LocalDate;

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

/** A single leave request. Package-private: only LeaveRequestService/Controller touch it. */
@Entity
@Table(name = "leave_requests")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class LeaveRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "employee_id", nullable = false)
    private long employeeId;

    @Column(name = "approver_id", nullable = false)
    private long approverId;

    @Column(name = "leave_type_id", nullable = false)
    private long leaveTypeId;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "working_days", nullable = false)
    private int workingDays;

    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RequestStatus status;

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "decision_comment")
    private String decisionComment;

    @Builder
    LeaveRequest(long employeeId, long approverId, long leaveTypeId,
                 LocalDate startDate, LocalDate endDate, int workingDays,
                 String notes, RequestStatus status, Instant submittedAt) {
        this.employeeId = employeeId;
        this.approverId = approverId;
        this.leaveTypeId = leaveTypeId;
        this.startDate = startDate;
        this.endDate = endDate;
        this.workingDays = workingDays;
        this.notes = notes;
        this.status = status;
        this.submittedAt = submittedAt;
    }

    void updatePendingDetails(LocalDate startDate, LocalDate endDate, int workingDays, String notes) {
        if (status != RequestStatus.PENDING) {
            throw new IllegalStateException("Only pending requests can be edited");
        }
        this.startDate = startDate;
        this.endDate = endDate;
        this.workingDays = workingDays;
        this.notes = notes;
    }
}
