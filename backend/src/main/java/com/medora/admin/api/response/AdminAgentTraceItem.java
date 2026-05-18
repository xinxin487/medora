package com.medora.admin.api.response;

import java.time.OffsetDateTime;

public record AdminAgentTraceItem(
        String traceId,
        String conversationId,
        String messageId,
        String intentType,
        String routeMode,
        String status,
        long latencyMs,
        String errorMessage,
        OffsetDateTime createdAt
) {
}
