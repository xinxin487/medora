package com.medora.memory.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medora.agent.application.AgentRunResult;
import com.medora.agent.application.IntentDecision;
import com.medora.agent.application.RouteMode;
import com.medora.agent.tool.ToolExecutionResult;
import com.medora.agent.tool.ToolNames;
import com.medora.auth.domain.UserAccountEntity;
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
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConversationSummaryServiceTest {

    @Test
    void shouldBuildSummaryAndBusinessStateFromScheduleQuery() {
        ConversationRepository conversationRepository = mock(ConversationRepository.class);
        ChatMessageRepository chatMessageRepository = mock(ChatMessageRepository.class);
        ConversationSummaryRepository conversationSummaryRepository = mock(ConversationSummaryRepository.class);
        ConversationSummaryService service = new ConversationSummaryService(
                conversationRepository,
                chatMessageRepository,
                conversationSummaryRepository,
                new KnowledgeJsonService(new ObjectMapper()),
                new ObjectMapper()
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
                "{}"
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
