package com.medora.common.api;

public record ErrorDetail(
        String field,
        String reason
) {
}
