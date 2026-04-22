package com.medora.user.api.response;

import java.util.List;

public record CurrentUserResponse(
        String userId,
        String displayName,
        String mobileMasked,
        String emailMasked,
        String defaultHospitalCode,
        DepartmentSimple defaultDepartment,
        List<String> roles
) {
    public record DepartmentSimple(
            String departmentId,
            String name
    ) {
    }
}
