package com.medora.masterdata.api.query;

public record DoctorListQuery(
        String departmentId,
        String keyword,
        String status,
        Integer pageNo,
        Integer pageSize
) {
}
