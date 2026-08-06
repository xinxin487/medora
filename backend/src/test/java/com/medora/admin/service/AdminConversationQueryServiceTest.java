package com.medora.admin.service;

import com.medora.auth.domain.UserAccountEntity;
import com.medora.chat.domain.ConversationEntity;
import com.medora.chat.repository.ChatMessageRepository;
import com.medora.chat.repository.ConversationRepository;
import com.medora.memory.domain.ConversationSummaryEntity;
import com.medora.memory.domain.UserMemoryProfileEntity;
import com.medora.memory.service.ConversationSummaryRepository;
import com.medora.memory.service.UserMemoryProfileRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdminConversationQueryServiceTest {

    @Test
    void shouldReturnConversationDetailWithSummaryAndMemory() {
        ConversationRepository conversationRepository = mock(ConversationRepository.class);
        ChatMessageRepository chatMessageRepository = mock(ChatMessageRepository.class);
        ConversationSummaryRepository conversationSummaryRepository = mock(ConversationSummaryRepository.class);
        UserMemoryProfileRepository userMemoryProfileRepository = mock(UserMemoryProfileRepository.class);

        AdminConversationQueryService service = new AdminConversationQueryService(
                conversationRepository,
                chatMessageRepository,
                conversationSummaryRepository,
                userMemoryProfileRepository
        );

        UserAccountEntity user = new UserAccountEntity();
        ReflectionTestUtils.setField(user, "id", 11L);
        ReflectionTestUtils.setField(user, "publicId", UUID.randomUUID());
        user.setUsername("admin-view-user");
        user.setStatus("ACTIVE");
        user.setRegisterSource("WEB");

        ConversationEntity conversation = ConversationEntity.create(user, "复诊咨询", "GENERAL");
        UUID conversationId = UUID.randomUUID();
        ReflectionTestUtils.setField(conversation, "id", 22L);
        ReflectionTestUtils.setField(conversation, "publicId", conversationId);
        ReflectionTestUtils.setField(conversation, "createdAt", OffsetDateTime.now().minusHours(2));
        conversation.touchLastMessageAt(OffsetDateTime.now().minusMinutes(5));

        ConversationSummaryEntity summary = ConversationSummaryEntity.create(conversation);
        summary.refresh("用户正在继续预约呼吸内科下午号", "{\"topic\":\"续约\"}", "{\"lastIntent\":\"BOOK_APPOINTMENT\"}", 8,
                OffsetDateTime.now().minusMinutes(2));

        UserMemoryProfileEntity memory = UserMemoryProfileEntity.create(
                user,
                "PREFERRED_PERIOD",
                "PM",
                "用户偏好下午时段",
                "{\"period\":\"PM\"}",
                0.92,
                "TOOL",
                OffsetDateTime.now().minusDays(1),
                null
        );
        ReflectionTestUtils.setField(memory, "publicId", UUID.randomUUID());

        when(conversationRepository.findByPublicId(conversationId)).thenReturn(Optional.of(conversation));
        when(conversationSummaryRepository.findByConversation(conversation)).thenReturn(Optional.of(summary));
        when(userMemoryProfileRepository.findTop10ByUserAccountAndStatusOrderByLastConfirmedAtDescCreatedAtDesc(user, "ACTIVE"))
                .thenReturn(List.of(memory));
        when(chatMessageRepository.countByConversation(conversation)).thenReturn(8L);

        var response = service.get(conversationId.toString());

        assertEquals("复诊咨询", response.title());
        assertEquals("用户正在继续预约呼吸内科下午号", response.summaryText());
        assertEquals(1, response.memories().size());
        assertEquals("admin-view-user", response.userLabel());
        assertFalse(response.summaryJson().isBlank());
    }
}
