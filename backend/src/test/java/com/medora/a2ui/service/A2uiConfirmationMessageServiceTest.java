package com.medora.a2ui.service;

import com.medora.auth.domain.UserAccountEntity;
import com.medora.chat.domain.ChatMessageEntity;
import com.medora.chat.domain.ConversationEntity;
import com.medora.chat.repository.ChatMessageRepository;
import com.medora.chat.repository.ConversationRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class A2uiConfirmationMessageServiceTest {
    @Test
    void persistsOneAssistantConfirmationInTheOriginatingConversation() {
        ConversationRepository conversations = mock(ConversationRepository.class);
        ChatMessageRepository messages = mock(ChatMessageRepository.class);
        UserAccountEntity user = new UserAccountEntity();
        ConversationEntity conversation = mock(ConversationEntity.class);
        UUID conversationId = UUID.randomUUID();
        when(conversations.findByPublicIdAndUserAccount(conversationId, user)).thenReturn(Optional.of(conversation));
        when(messages.findByConversationAndClientMessageId(conversation, "a2ui-confirmation-action-1")).thenReturn(Optional.empty());

        new A2uiConfirmationMessageService(conversations, messages)
                .record(conversationId, user, "action-1", "appointment-1");

        ArgumentCaptor<ChatMessageEntity> saved = ArgumentCaptor.forClass(ChatMessageEntity.class);
        verify(messages).save(saved.capture());
        assertThat(saved.getValue().getRole()).isEqualTo("ASSISTANT");
        assertThat(saved.getValue().getContent()).contains("已确认预约").contains("appointment-1");
    }
}
