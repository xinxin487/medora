package com.medora.common.api;

import java.time.OffsetDateTime;

public record ApiResponse<T>(
        String code,
        String message,
        T data,
        String requestId,
        OffsetDateTime timestamp
) {
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(
                "SUCCESS",
                "success",
                data,
                "phase0-placeholder-request-id",
                OffsetDateTime.now()
        );
    }
}
