package com.medora.admin.api.response;

import java.time.OffsetDateTime;

public record AdminToolAuditItem(
        String auditId, String toolName, String toolVersion, String sourceType, String riskLevel,
        String agentName, String conversationId, String traceId, String requestId, String status,
        String errorCode, long durationMs, String argumentsHash, String argumentsSummary, OffsetDateTime createdAt
) {
}
