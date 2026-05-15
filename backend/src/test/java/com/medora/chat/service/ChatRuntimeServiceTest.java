package com.medora.chat.service;

import com.medora.agent.application.ChatOrchestrator;
import com.medora.agent.trace.AgentTraceService;
import com.medora.auth.repository.UserAccountRepository;
import com.medora.auth.security.CurrentAuthenticatedUserProvider;
import com.medora.chat.repository.ChatMessageRepository;
import com.medora.chat.repository.ConversationRepository;
import com.medora.chat.sse.StreamEventPublisher;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class ChatRuntimeServiceTest {

    @Test
    void shouldBuildPhase4StubReplyWithUserMessage() {
        ChatRuntimeService service = new ChatRuntimeService(
                mock(CurrentAuthenticatedUserProvider.class),
                mock(UserAccountRepository.class),
                mock(ConversationRepository.class),
                mock(ChatMessageRepository.class),
                mock(TransactionTemplate.class),
                new SyncTaskExecutor(),
                mock(StreamEventPublisher.class),
                mock(ChatOrchestrator.class),
                mock(AgentTraceService.class)
        );

        String reply = service.buildStubAssistantReply("我最近咳嗽，应该挂哪个科？");

        assertTrue(reply.contains("我最近咳嗽"));
        assertTrue(reply.contains("Agent"));
        assertTrue(reply.contains("预约工具"));
    }
}
