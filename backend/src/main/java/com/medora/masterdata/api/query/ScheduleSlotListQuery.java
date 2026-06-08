package com.medora.masterdata.api.query;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record ScheduleSlotListQuery(
        @Size(max = 36) String departmentId,
        @Size(max = 36) String doctorId,
        @Size(max = 10) String scheduleDate,
        @Size(max = 10) String periodCode,
        @Size(max = 20) String status,
        @Min(1) Integer pageNo,
        @Min(1) @Max(100) Integer pageSize
) {
}
