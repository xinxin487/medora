package com.medora.chat.api.response;

import java.time.OffsetDateTime;

public record ConversationItem(
        String conversationId,
        String title,
        String sceneType,
        String status,
        OffsetDateTime lastMessageAt
) {
}
