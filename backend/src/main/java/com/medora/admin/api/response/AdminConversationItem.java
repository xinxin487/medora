package com.medora.admin.api.response;

import java.time.OffsetDateTime;

public record AdminConversationItem(
        String conversationId,
        String title,
        String sceneType,
        String status,
        String userId,
        String userLabel,
        long messageCount,
        String summaryText,
        OffsetDateTime lastMessageAt,
        OffsetDateTime createdAt
) {
}
