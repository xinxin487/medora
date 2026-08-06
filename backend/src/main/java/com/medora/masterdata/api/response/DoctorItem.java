package com.medora.masterdata.api.response;

public record DoctorItem(
        String doctorId,
        String code,
        String name,
        String title,
        String specialty,
        String status,
        DepartmentSimple department
) {
    public record DepartmentSimple(
            String departmentId,
            String name
    ) {
    }
}
