package com.medora.masterdata.api.response;

import java.math.BigDecimal;

public record DoctorDetailResponse(
        String doctorId,
        String code,
        String name,
        String title,
        String specialty,
        String intro,
        String status,
        BigDecimal consultationFee,
        DoctorItem.DepartmentSimple department
) {
}
