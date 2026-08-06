package com.medora.masterdata.api.response;

import java.math.BigDecimal;

public record ScheduleSlotItem(
        String slotId,
        String scheduleDate,
        String periodCode,
        String startTime,
        String endTime,
        Integer totalQuota,
        Integer availableQuota,
        BigDecimal fee,
        String status,
        DepartmentSimple department,
        DoctorSimple doctor
) {
    public record DepartmentSimple(
            String departmentId,
            String name
    ) {
    }

    public record DoctorSimple(
            String doctorId,
            String name,
            String title
    ) {
    }
}
