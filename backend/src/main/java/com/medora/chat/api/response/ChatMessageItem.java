package com.medora.chat.api.response;

import java.time.OffsetDateTime;

public record ChatMessageItem(
        String messageId,
        String role,
        String messageType,
        String content,
        String citations,
        OffsetDateTime createdAt
) {
}
