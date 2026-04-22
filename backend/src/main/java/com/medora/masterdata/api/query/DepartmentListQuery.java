package com.medora.masterdata.api.query;

public record DepartmentListQuery(
        String keyword,
        String status,
        Integer pageNo,
        Integer pageSize
) {
}
