package com.medora.memory.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medora.agent.application.AgentModelGateway;
import com.medora.agent.application.AgentRunResult;
import com.medora.agent.application.IntentDecision;
import com.medora.agent.application.RouteMode;
import com.medora.agent.tool.ToolExecutionResult;
import com.medora.agent.tool.ToolNames;
import com.medora.auth.domain.UserAccountEntity;
import com.medora.chat.cache.RedisContextCacheService;
import com.medora.chat.domain.ChatMessageEntity;
import com.medora.chat.domain.ConversationEntity;
import com.medora.chat.repository.ChatMessageRepository;
import com.medora.chat.repository.ConversationRepository;
import com.medora.common.api.PageResponse;
import com.medora.knowledge.service.KnowledgeJsonService;
import com.medora.masterdata.api.response.ScheduleSlotItem;
import com.medora.memory.domain.ConversationSummaryEntity;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.redis.RedisConnectionFailureException;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyString;

class ConversationSummaryServiceTest {

    @Test
    void capturesAnExplicitEarlyFactEvenWhenSummarizerIsUnavailable() {
        ConversationRepository conversations = mock(ConversationRepository.class);
        ChatMessageRepository messages = mock(ChatMessageRepository.class);
        ConversationSummaryRepository summaries = mock(ConversationSummaryRepository.class);
        AgentModelGateway gateway = mock(AgentModelGateway.class);
        when(gateway.generateConversationSummary(anyString(), anyString())).thenReturn(Optional.empty());
        ConversationEntity conversation = ConversationEntity.create(new UserAccountEntity(), "咨询", "GENERAL");
        when(summaries.findByConversation(conversation)).thenReturn(Optional.empty());
        when(messages.findTop12ByConversationOrderByCreatedAtDescIdDesc(conversation)).thenReturn(List.of());
        when(messages.countByConversation(conversation)).thenReturn(2L);
        ConversationSummaryService service = new ConversationSummaryService(conversations, messages, summaries,
                new KnowledgeJsonService(new ObjectMapper()), new ObjectMapper(),
                mock(RedisContextCacheService.class), gateway);
        AgentRunResult run = new AgentRunResult(new IntentDecision("ADK_ROUTED", RouteMode.CHAT_ONLY, false, List.of()),
                "知道了", List.of(), List.of(), "[]", "{}", "{}", "[]", "LOW", "[]");

        service.refreshAfterAssistantReply(conversation, "我对青霉素过敏", "知道了", run);

        ArgumentCaptor<ConversationSummaryEntity> captured = ArgumentCaptor.forClass(ConversationSummaryEntity.class);
        verify(summaries).save(captured.capture());
        assertTrue(captured.getValue().getSummaryText().contains("青霉素过敏"));
    }

    @Test
    void doesNotTreatAnAllergyQuestionAsAConfirmedFact() {
        ConversationRepository conversations = mock(ConversationRepository.class);
        ChatMessageRepository messages = mock(ChatMessageRepository.class);
        ConversationSummaryRepository summaries = mock(ConversationSummaryRepository.class);
        AgentModelGateway gateway = mock(AgentModelGateway.class);
        when(gateway.generateConversationSummary(anyString(), anyString())).thenReturn(Optional.empty());
        ConversationEntity conversation = ConversationEntity.create(new UserAccountEntity(), "咨询", "GENERAL");
        when(summaries.findByConversation(conversation)).thenReturn(Optional.empty());
        when(messages.countByConversation(conversation)).thenReturn(2L);
        ConversationSummaryService service = new ConversationSummaryService(conversations, messages, summaries,
                new KnowledgeJsonService(new ObjectMapper()), new ObjectMapper(),
                mock(RedisContextCacheService.class), gateway);
        AgentRunResult run = new AgentRunResult(new IntentDecision("ADK_ROUTED", RouteMode.CHAT_ONLY, false, List.of()),
                "请就医确认", List.of(), List.of(), "[]", "{}", "{}", "[]", "LOW", "[]");

        for (String message : List.of("我对青霉素过敏吗？", "如果我对青霉素过敏该怎么办？",
                "我对青霉素过敏会怎样？")) {
            service.refreshAfterAssistantReply(conversation, message, "请就医确认", run);
        }

        ArgumentCaptor<ConversationSummaryEntity> captured = ArgumentCaptor.forClass(ConversationSummaryEntity.class);
        verify(summaries, org.mockito.Mockito.times(3)).save(captured.capture());
        assertTrue(captured.getAllValues().stream().noneMatch(entity ->
                entity.getSummaryText().contains("已确认：用户自述对青霉素过敏")));
    }

    @Test
    void retainsAnEarlyConfirmedFactAfterMoreThanThirtyMessages() {
        ConversationRepository conversations = mock(ConversationRepository.class);
        ChatMessageRepository messages = mock(ChatMessageRepository.class);
        ConversationSummaryRepository summaries = mock(ConversationSummaryRepository.class);
        AgentModelGateway gateway = mock(AgentModelGateway.class);
        when(gateway.generateConversationSummary(anyString(), anyString())).thenReturn(Optional.empty());
        ConversationSummaryService service = new ConversationSummaryService(conversations, messages, summaries,
                new KnowledgeJsonService(new ObjectMapper()), new ObjectMapper(),
                mock(RedisContextCacheService.class), gateway);
        ConversationEntity conversation = mock(ConversationEntity.class);
        when(conversation.getPublicId()).thenReturn(UUID.randomUUID());
        ConversationSummaryEntity old = ConversationSummaryEntity.create(conversation);
        old.refresh("当前主题：一般咨询。已确认：患者对青霉素过敏。",
                "{\"confirmedFacts\":[\"患者对青霉素过敏\"]}", "{}", 31, java.time.OffsetDateTime.now());
        when(summaries.findByConversation(conversation)).thenReturn(Optional.of(old));
        when(messages.findTop12ByConversationOrderByCreatedAtDescIdDesc(conversation)).thenReturn(List.of());
        when(messages.countByConversation(conversation)).thenReturn(33L);
        AgentRunResult run = new AgentRunResult(new IntentDecision("ADK_ROUTED", RouteMode.CHAT_ONLY, false, List.of()),
                "好的", List.of(), List.of(), "[]", "{}", "{}", "[]", "LOW", "[]");

        service.refreshAfterAssistantReply(conversation, "谢谢", "好的", run);

        assertTrue(old.getSummaryText().contains("患者对青霉素过敏"));
    }

    @Test
    void boundedSummaryKeepsEarlyAnchorAndAcceptsNewConfirmedFacts() throws Exception {
        ConversationRepository conversations = mock(ConversationRepository.class);
        ChatMessageRepository messages = mock(ChatMessageRepository.class);
        ConversationSummaryRepository summaries = mock(ConversationSummaryRepository.class);
        AgentModelGateway gateway = mock(AgentModelGateway.class);
        when(gateway.generateConversationSummary(anyString(), anyString())).thenReturn(Optional.empty());
        ObjectMapper mapper = new ObjectMapper();
        ConversationEntity conversation = ConversationEntity.create(new UserAccountEntity(), "咨询", "GENERAL");
        ConversationSummaryEntity old = ConversationSummaryEntity.create(conversation);
        old.refresh("旧摘要", mapper.writeValueAsString(java.util.Map.of("confirmedFacts",
                java.util.stream.IntStream.rangeClosed(1, 12).mapToObj(index -> "早期事实" + index).toList())),
                "{}", 31, java.time.OffsetDateTime.now());
        when(summaries.findByConversation(conversation)).thenReturn(Optional.of(old));
        when(messages.countByConversation(conversation)).thenReturn(33L);
        ConversationSummaryService service = new ConversationSummaryService(conversations, messages, summaries,
                new KnowledgeJsonService(mapper), mapper, mock(RedisContextCacheService.class), gateway);
        AgentRunResult run = new AgentRunResult(new IntentDecision("ADK_ROUTED", RouteMode.CHAT_ONLY, false, List.of()),
                "知道了", List.of(), List.of(), "[]", "{}", "{}", "[]", "LOW", "[]");

        service.refreshAfterAssistantReply(conversation, "我对青霉素过敏", "知道了", run);

        assertTrue(old.getSummaryText().contains("早期事实1"));
        assertTrue(old.getSummaryText().contains("青霉素过敏"));
        assertTrue(mapper.readTree(old.getSummaryJson()).get("confirmedFacts").size() <= 12);
    }

    @Test
    void newExplicitPeriodPreferenceSupersedesOldSummaryPreference() {
        ConversationRepository conversations = mock(ConversationRepository.class);
        ChatMessageRepository messages = mock(ChatMessageRepository.class);
        ConversationSummaryRepository summaries = mock(ConversationSummaryRepository.class);
        AgentModelGateway gateway = mock(AgentModelGateway.class);
        when(gateway.generateConversationSummary(anyString(), anyString()))
                .thenReturn(Optional.of("{\"topic\":\"一般咨询\",\"confirmedFacts\":[\"用户偏好下午号源\"]}"));
        ConversationEntity conversation = ConversationEntity.create(new UserAccountEntity(), "咨询", "GENERAL");
        ConversationSummaryEntity old = ConversationSummaryEntity.create(conversation);
        old.refresh("已确认：用户偏好上午号源", "{\"confirmedFacts\":[\"用户偏好上午号源\"]}",
                "{}", 20, OffsetDateTime.now());
        when(summaries.findByConversation(conversation)).thenReturn(Optional.of(old));
        when(messages.countByConversation(conversation)).thenReturn(22L);
        ConversationSummaryService service = new ConversationSummaryService(conversations, messages, summaries,
                new KnowledgeJsonService(new ObjectMapper()), new ObjectMapper(),
                mock(RedisContextCacheService.class), gateway);
        AgentRunResult run = new AgentRunResult(new IntentDecision("ADK_ROUTED", RouteMode.CHAT_ONLY, false, List.of()),
                "好的", List.of(), List.of(), "[]", "{}", "{}", "[]", "LOW", "[]");

        service.refreshAfterAssistantReply(conversation, "我更喜欢下午", "好的", run);

        assertTrue(!old.getSummaryText().contains("偏好上午"));
        assertTrue(old.getSummaryText().contains("偏好下午"));
    }

    @Test
    void fallsBackToDatabaseWhenRedisReadFails() {
        ConversationRepository conversations = mock(ConversationRepository.class);
        ChatMessageRepository messages = mock(ChatMessageRepository.class);
        RedisContextCacheService redis = mock(RedisContextCacheService.class);
        ConversationEntity conversation = mock(ConversationEntity.class);
        when(conversation.getPublicId()).thenReturn(UUID.randomUUID());
        when(conversations.findById(2L)).thenReturn(Optional.of(conversation));
        when(redis.getRecentMessages(conversation.getPublicId().toString()))
                .thenThrow(new RedisConnectionFailureException("down"));
        when(messages.findTop12ByConversationOrderByCreatedAtDescIdDesc(conversation))
                .thenReturn(List.of(ChatMessageEntity.userText(conversation, "咳嗽三天", null)));
        ConversationSummaryService service = new ConversationSummaryService(conversations, messages,
                mock(ConversationSummaryRepository.class), new KnowledgeJsonService(new ObjectMapper()),
                new ObjectMapper(), redis, mock(AgentModelGateway.class));

        assertTrue(service.loadContext(2L).recentMessages().stream()
                .anyMatch(message -> message.content().contains("咳嗽三天")));
    }

    @Test
    void shouldBuildSummaryAndBusinessStateFromScheduleQuery() {
        ConversationRepository conversationRepository = mock(ConversationRepository.class);
        ChatMessageRepository chatMessageRepository = mock(ChatMessageRepository.class);
        ConversationSummaryRepository conversationSummaryRepository = mock(ConversationSummaryRepository.class);
        AgentModelGateway gateway = mock(AgentModelGateway.class);
        when(gateway.generateConversationSummary(anyString(), anyString())).thenReturn(Optional.empty());
        ConversationSummaryService service = new ConversationSummaryService(
                conversationRepository,
                chatMessageRepository,
                conversationSummaryRepository,
                new KnowledgeJsonService(new ObjectMapper()),
                new ObjectMapper(),
                mock(RedisContextCacheService.class),
                gateway
        );
        ConversationEntity conversation = ConversationEntity.create(new UserAccountEntity(), "导诊咨询", "GENERAL");
        ChatMessageEntity userMessage = ChatMessageEntity.userText(conversation, "帮我查明天下午呼吸内科号源", null);
        ChatMessageEntity assistantMessage = ChatMessageEntity.assistantText(conversation, "查到 1 条号源");
        assistantMessage.completeAssistantContent("查到 1 条号源", "{}", "[]", "{}");

        when(chatMessageRepository.findTop12ByConversationOrderByCreatedAtDescIdDesc(conversation))
                .thenReturn(List.of(assistantMessage, userMessage));
        when(chatMessageRepository.countByConversation(conversation)).thenReturn(2L);
        when(conversationSummaryRepository.findByConversation(conversation)).thenReturn(Optional.empty());

        AgentRunResult runResult = new AgentRunResult(
                new IntentDecision("QUERY_SCHEDULE_SLOTS", RouteMode.TOOL_ONLY, false, List.of()),
                "查到 1 条号源",
                List.of(),
                List.of(ToolExecutionResult.success(
                        ToolNames.QUERY_SCHEDULE_SLOTS,
                        new PageResponse<>(List.of(slot()), 1, 1, 20, 1, false),
                        "已找到 1 条号源"
                )),
                "[]",
                "{}",
                "{}",
                "[]",
                "LOW",
                "[]"
        );

        service.refreshAfterAssistantReply(conversation, "帮我查明天下午呼吸内科号源", "查到 1 条号源", runResult);

        ArgumentCaptor<ConversationSummaryEntity> captor = ArgumentCaptor.forClass(ConversationSummaryEntity.class);
        verify(conversationSummaryRepository).save(captor.capture());
        ConversationSummaryEntity saved = captor.getValue();
        assertTrue(saved.getSummaryText().contains("号源查询"));
        assertTrue(saved.getSummaryText().contains("等待用户提供 slotId 继续预约"));
        assertTrue(saved.getBusinessStateJson().contains("lastSlotId"));
    }

    private ScheduleSlotItem slot() {
        return new ScheduleSlotItem(
                "slot-001",
                "2026-05-19",
                "PM",
                "14:00",
                "14:30",
                10,
                3,
                BigDecimal.valueOf(16),
                "OPEN",
                new ScheduleSlotItem.DepartmentSimple("dept-001", "呼吸内科"),
                new ScheduleSlotItem.DoctorSimple("doctor-001", "李医生", "主任医师")
        );
    }
}
