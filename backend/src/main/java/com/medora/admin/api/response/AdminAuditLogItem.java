package com.medora.admin.api.response;

import java.time.OffsetDateTime;

public record AdminAuditLogItem(
        String auditLogId,
        String userId,
        String userLabel,
        String operatorType,
        String actionType,
        String resourceType,
        String resourceId,
        String requestId,
        String detailJson,
        OffsetDateTime createdAt
) {
}
