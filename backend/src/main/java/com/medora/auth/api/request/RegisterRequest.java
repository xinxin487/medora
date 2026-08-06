package com.medora.auth.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        String mobile,
        String email,
        @NotBlank(message = "password is required")
        @Size(min = 6, max = 64, message = "password length must be between 6 and 64")
        String password,
        @NotBlank(message = "displayName is required")
        @Size(max = 64, message = "displayName must not exceed 64 characters")
        String displayName
) {
}
