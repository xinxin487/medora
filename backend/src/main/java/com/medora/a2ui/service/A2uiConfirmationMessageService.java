package com.medora.a2ui.service;

import com.medora.auth.domain.UserAccountEntity;
import com.medora.chat.domain.ChatMessageEntity;
import com.medora.chat.repository.ChatMessageRepository;
import com.medora.chat.repository.ConversationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
public class A2uiConfirmationMessageService {
    private final ConversationRepository conversations;
    private final ChatMessageRepository messages;

    public A2uiConfirmationMessageService(ConversationRepository conversations, ChatMessageRepository messages) {
        this.conversations = conversations;
        this.messages = messages;
    }

    @Transactional
    public void record(UUID conversationId, UserAccountEntity user, String actionId, String appointmentId) {
        if (conversationId == null) return;
        var conversation = conversations.findByPublicIdAndUserAccount(conversationId, user).orElse(null);
        if (conversation == null) return;
        String clientMessageId = "a2ui-confirmation-" + actionId;
        if (messages.findByConversationAndClientMessageId(conversation, clientMessageId).isPresent()) return;
        messages.save(ChatMessageEntity.assistantText(conversation,
                "已确认预约，预约编号为 " + appointmentId + "。如需查看或取消预约，随时告诉我。", clientMessageId));
        conversation.touchLastMessageAt(OffsetDateTime.now());
    }
}
