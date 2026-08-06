package com.medora.admin.service;

import com.medora.agent.trace.AgentTraceEntity;
import com.medora.agent.trace.AgentTraceRepository;
import com.medora.auth.domain.UserAccountEntity;
import com.medora.chat.domain.ChatMessageEntity;
import com.medora.chat.domain.ConversationEntity;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdminTraceQueryServiceTest {

    @Test
    void shouldReturnTraceDetail() {
        AgentTraceRepository repository = mock(AgentTraceRepository.class);
        AdminTraceQueryService service = new AdminTraceQueryService(repository);

        UserAccountEntity user = new UserAccountEntity();
        ReflectionTestUtils.setField(user, "publicId", UUID.randomUUID());
        user.setStatus("ACTIVE");
        user.setRegisterSource("WEB");

        ConversationEntity conversation = ConversationEntity.create(user, "导诊", "GENERAL");
        ReflectionTestUtils.setField(conversation, "publicId", UUID.randomUUID());

        ChatMessageEntity message = ChatMessageEntity.assistantText(conversation, "已为你查到下午号");
        ReflectionTestUtils.setField(message, "publicId", UUID.randomUUID());

        AgentTraceEntity trace = AgentTraceEntity.of(
                "trace_phase8_001",
                conversation,
                message,
                "BOOK_APPOINTMENT",
                "TOOL_ONLY",
                "[{\"sourceId\":\"doc-1\"}]",
                "[{\"toolName\":\"bookAppointment\",\"status\":\"SUCCESS\"}]",
                168,
                "SUCCESS",
                null
        );
        ReflectionTestUtils.setField(trace, "createdAt", OffsetDateTime.now().minusMinutes(3));

        when(repository.findByTraceId("trace_phase8_001")).thenReturn(Optional.of(trace));

        var response = service.get("trace_phase8_001");

        assertEquals("BOOK_APPOINTMENT", response.intentType());
        assertEquals("TOOL_ONLY", response.routeMode());
        assertEquals("SUCCESS", response.status());
        assertEquals(168, response.latencyMs());
        assertEquals("[{\"sourceId\":\"doc-1\"}]", response.retrievalJson());
    }
}
