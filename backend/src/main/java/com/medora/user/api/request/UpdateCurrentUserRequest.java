package com.medora.user.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateCurrentUserRequest(
        @NotBlank(message = "displayName is required")
        @Size(max = 64, message = "displayName must not exceed 64 characters")
        String displayName,
        String defaultHospitalCode,
        String defaultDepartmentId
) {
}
