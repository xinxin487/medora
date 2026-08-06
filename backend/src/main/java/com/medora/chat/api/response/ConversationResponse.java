package com.medora.chat.api.response;

public record ConversationResponse(
        String conversationId,
        String title,
        String sceneType,
        String status
) {
}
