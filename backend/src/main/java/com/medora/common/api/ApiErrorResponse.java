package com.medora.common.api;

import com.medora.common.web.RequestMetadata;

import java.time.OffsetDateTime;
import java.util.List;

public record ApiErrorResponse(
        String code,
        String message,
        List<ErrorDetail> details,
        String requestId,
        OffsetDateTime timestamp
) {
    public static ApiErrorResponse of(String code, String message, List<ErrorDetail> details) {
        return new ApiErrorResponse(
                code,
                message,
                details,
                RequestMetadata.currentRequestId(),
                OffsetDateTime.now()
        );
    }
}
