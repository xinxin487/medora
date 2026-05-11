package com.medora.chat.service;

import com.medora.chat.api.response.ChatMessageItem;
import com.medora.chat.api.response.ConversationItem;
import com.medora.chat.api.response.ConversationResponse;
import com.medora.chat.domain.ChatMessageEntity;
import com.medora.chat.domain.ConversationEntity;

final class ConversationResponseMapper {

    private ConversationResponseMapper() {
    }

    static ConversationResponse toResponse(ConversationEntity entity) {
        return new ConversationResponse(
                entity.getPublicId().toString(),
                entity.getTitle(),
                entity.getSceneType(),
                entity.getStatus()
        );
    }

    static ConversationItem toItem(ConversationEntity entity) {
        return new ConversationItem(
                entity.getPublicId().toString(),
                entity.getTitle(),
                entity.getSceneType(),
                entity.getStatus(),
                entity.getLastMessageAt()
        );
    }

    static ChatMessageItem toMessageItem(ChatMessageEntity entity) {
        return new ChatMessageItem(
                entity.getPublicId().toString(),
                entity.getRole(),
                entity.getMessageType(),
                entity.getContent(),
                entity.getCitationJson(),
                entity.getCreatedAt()
        );
    }
}
