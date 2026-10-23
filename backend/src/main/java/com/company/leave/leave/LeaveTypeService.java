package com.company.leave.leave;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Public, read-only seam for leave type reference data. */
@Service
public class LeaveTypeService {

    private final LeaveTypeRepository leaveTypeRepository;

    LeaveTypeService(LeaveTypeRepository leaveTypeRepository) {
        this.leaveTypeRepository = leaveTypeRepository;
    }

    @Transactional(readOnly = true)
    public List<LeaveTypeResponse> listLeaveTypes() {
        return leaveTypeRepository.findAllByOrderByIdAsc().stream()
                .map(leaveType -> LeaveTypeResponse.builder()
                        .id(leaveType.getId())
                        .name(leaveType.getName())
                        .build())
                .toList();
    }

    /** Returns {@code true} if a leave type with the given id exists. */
    @Transactional(readOnly = true)
    public boolean existsById(long leaveTypeId) {
        return leaveTypeRepository.existsById(leaveTypeId);
    }
}
