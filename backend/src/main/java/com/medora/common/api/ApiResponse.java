package com.medora.common.api;

import com.medora.common.web.RequestMetadata;

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
                RequestMetadata.currentRequestId(),
                OffsetDateTime.now()
        );
    }
}
