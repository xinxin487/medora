package com.medora.admin.api.response;

import java.time.OffsetDateTime;
import java.util.List;

public record AdminConversationDetailResponse(
        String conversationId,
        String title,
        String sceneType,
        String status,
        String userId,
        String userLabel,
        long messageCount,
        OffsetDateTime createdAt,
        OffsetDateTime lastMessageAt,
        String summaryText,
        String summaryJson,
        String businessStateJson,
        OffsetDateTime lastSummarizedAt,
        List<AdminMemoryProfileItem> memories
) {
}
