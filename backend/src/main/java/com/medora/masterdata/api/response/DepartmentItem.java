package com.medora.masterdata.api.response;

public record DepartmentItem(
        String departmentId,
        String code,
        String name,
        String category,
        String description,
        String status
) {
}
