package com.medora.masterdata.api.query;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record DepartmentListQuery(
        @Size(max = 50) String keyword,
        @Size(max = 20) String status,
        @Min(1) Integer pageNo,
        @Min(1) @Max(100) Integer pageSize
) {
}
