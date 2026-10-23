package com.company.leave.leave;

import java.util.List;
import java.util.Optional;

import org.springframework.data.repository.Repository;

/** Read-only persistence seam for leave type reference data. */
interface LeaveTypeRepository extends Repository<LeaveType, Long> {

    List<LeaveType> findAllByOrderByIdAsc();

    Optional<LeaveType> findById(Long id);

    boolean existsById(Long id);
}
