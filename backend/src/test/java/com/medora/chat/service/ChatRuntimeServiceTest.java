package com.medora.chat.service;

import com.medora.agent.application.ChatOrchestrator;
import com.medora.agent.trace.AgentTraceService;
import com.medora.auth.repository.UserAccountRepository;
import com.medora.auth.security.CurrentAuthenticatedUserProvider;
import com.medora.chat.cache.RedisContextCacheService;
import com.medora.chat.repository.ChatMessageRepository;
import com.medora.chat.repository.ConversationRepository;
import com.medora.chat.sse.StreamEventPublisher;
import com.medora.memory.service.ConversationSummaryService;
import com.medora.memory.service.MemoryService;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import com.medora.agent.application.AgentRunResult;
import org.springframework.data.redis.RedisConnectionFailureException;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.any;

class ChatRuntimeServiceTest {

    @Test
    void redisWriteFailureDoesNotTurnCommittedAssistantReplyIntoFailure() {
        TransactionTemplate transaction = mock(TransactionTemplate.class);
        doReturn("conversation-public-id").when(transaction).execute(any());
        RedisContextCacheService cache = mock(RedisContextCacheService.class);
        doThrow(new RedisConnectionFailureException("down"))
                .when(cache).pushMessage("conversation-public-id", "USER", "问题");
        ChatRuntimeService service = new ChatRuntimeService(
                mock(CurrentAuthenticatedUserProvider.class), mock(UserAccountRepository.class),
                mock(ConversationRepository.class), mock(ChatMessageRepository.class), transaction,
                new SyncTaskExecutor(), mock(StreamEventPublisher.class), mock(ChatOrchestrator.class),
                mock(AgentTraceService.class), mock(ConversationSummaryService.class),
                mock(MemoryService.class), cache);

        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> ReflectionTestUtils.invokeMethod(service,
                "completeAssistantMessage", 1L, 2L, "trace", "问题", "回答", mock(AgentRunResult.class), 10L));
        verify(cache).pushMessage("conversation-public-id", "ASSISTANT", "回答");
    }

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
                mock(AgentTraceService.class),
                mock(ConversationSummaryService.class),
                mock(MemoryService.class),
                mock(RedisContextCacheService.class)
        );

        String reply = service.buildStubAssistantReply("我最近咳嗽，应该挂哪个科？");

        assertTrue(reply.contains("我最近咳嗽"));
        assertTrue(reply.contains("Agent"));
        assertTrue(reply.contains("预约工具"));
    }
}
