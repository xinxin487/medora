package com.medora.auth.api.request;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "account is required")
        String account,
        @NotBlank(message = "password is required")
        String password,
        @NotBlank(message = "loginType is required")
        String loginType
) {
}
