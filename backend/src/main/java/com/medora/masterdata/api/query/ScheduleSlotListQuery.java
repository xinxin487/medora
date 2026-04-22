package com.medora.masterdata.api.query;

public record ScheduleSlotListQuery(
        String departmentId,
        String doctorId,
        String scheduleDate,
        String periodCode,
        String status,
        Integer pageNo,
        Integer pageSize
) {
}
