package com.medora.admin.api.response;

import java.time.OffsetDateTime;

public record AdminChatMessageItem(
        String messageId,
        String role,
        String messageType,
        String content,
        String citationJson,
        String toolCallJson,
        String tokenUsageJson,
        OffsetDateTime createdAt
) {
}
