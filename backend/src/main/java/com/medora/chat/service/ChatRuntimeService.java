package com.medora.chat.service;

import com.medora.agent.application.AgentChatCommand;
import com.medora.agent.application.AgentRunResult;
import com.medora.agent.application.ChatOrchestrator;
import com.medora.agent.trace.AgentTraceService;
import com.medora.auth.domain.UserAccountEntity;
import com.medora.auth.repository.UserAccountRepository;
import com.medora.auth.security.AuthenticatedUser;
import com.medora.auth.security.CurrentAuthenticatedUserProvider;
import com.medora.chat.cache.RedisContextCacheService;
import com.medora.chat.api.request.ChatStreamRequest;
import com.medora.chat.domain.ChatMessageEntity;
import com.medora.chat.domain.ConversationEntity;
import com.medora.chat.repository.ChatMessageRepository;
import com.medora.chat.repository.ConversationRepository;
import com.medora.chat.sse.StreamEventPublisher;
import com.medora.common.exception.BusinessException;
import com.medora.memory.service.ConversationSummaryService;
import com.medora.memory.service.MemoryService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class ChatRuntimeService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ChatRuntimeService.class);

    private final CurrentAuthenticatedUserProvider currentAuthenticatedUserProvider;
    private final UserAccountRepository userAccountRepository;
    private final ConversationRepository conversationRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final TransactionTemplate transactionTemplate;
    private final TaskExecutor taskExecutor;
    private final StreamEventPublisher streamEventPublisher;
    private final ChatOrchestrator chatOrchestrator;
    private final AgentTraceService agentTraceService;
    private final ConversationSummaryService conversationSummaryService;
    private final MemoryService memoryService;
    private final RedisContextCacheService redisContextCacheService;
    private final Map<String, ActiveStreamHandle> activeStreams = new ConcurrentHashMap<>();

    public ChatRuntimeService(CurrentAuthenticatedUserProvider currentAuthenticatedUserProvider,
                              UserAccountRepository userAccountRepository,
                              ConversationRepository conversationRepository,
                              ChatMessageRepository chatMessageRepository,
                              TransactionTemplate transactionTemplate,
                              @Qualifier("chatStreamExecutor") TaskExecutor taskExecutor,
                              StreamEventPublisher streamEventPublisher,
                              ChatOrchestrator chatOrchestrator,
                              AgentTraceService agentTraceService,
                              ConversationSummaryService conversationSummaryService,
                              MemoryService memoryService,
                              RedisContextCacheService redisContextCacheService) {
        this.currentAuthenticatedUserProvider = currentAuthenticatedUserProvider;
        this.userAccountRepository = userAccountRepository;
        this.conversationRepository = conversationRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.transactionTemplate = transactionTemplate;
        this.taskExecutor = taskExecutor;
        this.streamEventPublisher = streamEventPublisher;
        this.chatOrchestrator = chatOrchestrator;
        this.agentTraceService = agentTraceService;
        this.conversationSummaryService = conversationSummaryService;
        this.memoryService = memoryService;
        this.redisContextCacheService = redisContextCacheService;
    }

    public SseEmitter stream(String conversationId, ChatStreamRequest request) {
        Long userAccountId = getCurrentUserAccount().getId();
        SseEmitter emitter = new SseEmitter(60_000L);
        AtomicBoolean cancelled = new AtomicBoolean(false);
        activeStreams.put(conversationId, new ActiveStreamHandle(emitter, cancelled));
        emitter.onCompletion(() -> activeStreams.remove(conversationId));
        emitter.onTimeout(() -> activeStreams.remove(conversationId));
        emitter.onError(e -> activeStreams.remove(conversationId));
        taskExecutor.execute(() -> runStream(emitter, userAccountId, conversationId, request, cancelled));
        return emitter;
    }

    public boolean stopStream(String conversationId) {
        ActiveStreamHandle handle = activeStreams.remove(conversationId);
        if (handle == null) {
            return false;
        }
        handle.cancelled.set(true);
        handle.emitter.complete();
        return true;
    }

    String buildStubAssistantReply(String userMessage) {
        String normalized = userMessage == null ? "" : userMessage.trim();
        if (normalized.length() > 80) {
            normalized = normalized.substring(0, 80) + "...";
        }
        return "已收到你的问题：“" + normalized
                + "”。当前阶段已接入 Agent 编排、知识检索和预约工具，你可以继续查询号源、预约或取消挂号。";
    }

    private void runStream(SseEmitter emitter, Long userAccountId, String conversationId,
                           ChatStreamRequest request, AtomicBoolean cancelled) {
        StreamSession session = null;
        boolean assistantCompleted = false;
        long startedAt = System.nanoTime();
        try {
            session = createStreamSession(userAccountId, conversationId, request);
            streamEventPublisher.publishMessageStart(
                    emitter,
                    session.assistantMessagePublicId(),
                    session.conversationPublicId(),
                    session.traceId()
            );

            AgentRunResult agentRunResult = chatOrchestrator.handle(
                    new AgentChatCommand(
                            userAccountId,
                            session.conversationId(),
                            session.assistantMessageId(),
                            session.traceId(),
                            request.message()
                    ),
                    emitter,
                    cancelled
            );
            String reply = agentRunResult.reply();
            completeAssistantMessage(
                    session.assistantMessageId(),
                    session.conversationId(),
                    session.traceId(),
                    request.message(),
                    reply,
                    agentRunResult,
                    elapsedMillis(startedAt)
            );
            assistantCompleted = true;
            streamEventPublisher.publishMessageEnd(emitter, session.assistantMessagePublicId());
            emitter.complete();
        } catch (Exception ex) {
            if (session != null && !assistantCompleted) {
                completeAssistantFailure(session, ex, elapsedMillis(startedAt));
            }
            publishErrorAndComplete(emitter, ex);
        }
    }

    private StreamSession createStreamSession(Long userAccountId, String conversationId, ChatStreamRequest request) {
        return transactionTemplate.execute(status -> {
            UserAccountEntity userAccount = userAccountRepository.findById(userAccountId)
                    .orElseThrow(() -> new BusinessException("USER_NOT_FOUND", HttpStatus.NOT_FOUND,
                            "current user not found"));
            ConversationEntity conversation = conversationRepository
                    .findByPublicIdAndUserAccount(parseUuid(conversationId), userAccount)
                    .orElseThrow(() -> new BusinessException("CHAT_CONVERSATION_NOT_FOUND", HttpStatus.NOT_FOUND,
                            "conversation not found"));

            String clientMessageId = normalizeToNull(request.clientMessageId());
            if (clientMessageId == null
                    || chatMessageRepository.findByConversationAndClientMessageId(conversation, clientMessageId).isEmpty()) {
                chatMessageRepository.save(ChatMessageEntity.userText(conversation, request.message().trim(), clientMessageId));
            }

            ChatMessageEntity assistantMessage = chatMessageRepository.save(
                    ChatMessageEntity.assistantText(conversation, "")
            );
            OffsetDateTime now = OffsetDateTime.now();
            conversation.touchLastMessageAt(now);

            return new StreamSession(
                    conversation.getId(),
                    conversation.getPublicId().toString(),
                    assistantMessage.getId(),
                    assistantMessage.getPublicId().toString(),
                    "trace_" + UUID.randomUUID().toString().replace("-", ""),
                    request.message()
            );
        });
    }

    private void completeAssistantMessage(Long assistantMessageId,
                                          Long conversationId,
                                          String traceId,
                                          String userMessage,
                                          String reply,
                                          AgentRunResult agentRunResult,
                                          long latencyMs) {
        String conversationPublicId = transactionTemplate.execute(status -> {
            ChatMessageEntity assistantMessage = chatMessageRepository.findById(assistantMessageId)
                    .orElseThrow(() -> new BusinessException("CHAT_MESSAGE_NOT_FOUND", HttpStatus.NOT_FOUND,
                            "assistant message not found"));
            assistantMessage.completeAssistantContent(
                    reply,
                    agentRunResult.tokenUsageJson(),
                    agentRunResult.citationJson(),
                    agentRunResult.toolCallJson()
            );
            ConversationEntity conversation = conversationRepository.findById(conversationId)
                    .orElseThrow(() -> new BusinessException("CHAT_CONVERSATION_NOT_FOUND", HttpStatus.NOT_FOUND,
                            "conversation not found"));
            conversation.touchLastMessageAt(OffsetDateTime.now());
            agentTraceService.record(new AgentTraceService.AgentTraceRecord(
                    traceId,
                    conversation,
                    assistantMessage,
                    agentRunResult.intentDecision().intentType(),
                    agentRunResult.intentDecision().routeMode().name(),
                    agentRunResult.citationJson(),
                    agentRunResult.toolCallJson(),
                    latencyMs,
                    "SUCCESS",
                    null,
                    agentRunResult.safetyEventsJson(),
                    agentRunResult.hallucinationRisk(),
                    agentRunResult.riskFactorsJson()
            ));
            conversationSummaryService.refreshAfterAssistantReply(conversation, userMessage, reply, agentRunResult);
            memoryService.captureFromAgentRun(conversation.getUserAccount(), userMessage, agentRunResult);
            return conversation.getPublicId().toString();
        });
        if (conversationPublicId != null) {
            redisContextCacheService.pushMessage(conversationPublicId, "USER", userMessage);
            redisContextCacheService.pushMessage(conversationPublicId, "ASSISTANT", reply);
        }
    }

    private void completeAssistantFailure(StreamSession session, Exception ex, long latencyMs) {
        String conversationPublicId = transactionTemplate.execute(status -> {
            ChatMessageEntity assistantMessage = chatMessageRepository.findById(session.assistantMessageId())
                    .orElseThrow(() -> new BusinessException("CHAT_MESSAGE_NOT_FOUND", HttpStatus.NOT_FOUND,
                            "assistant message not found"));
            String errorCode = ex instanceof BusinessException be ? be.getCode() : null;
            String errorMessage = ex instanceof BusinessException ? ex.getMessage() : "chat stream failed";
            boolean isSafetyRejection = "AGENT_SAFETY_REJECTED".equals(errorCode);
            String reply = isSafetyRejection
                    ? "抱歉，你的消息已被安全策略拦截。请调整后重新提问。"
                    : "抱歉，这次对话处理失败：" + errorMessage;
            assistantMessage.completeAssistantContent(
                    reply,
                    "{\"inputTokens\":0,\"outputTokens\":0}",
                    "[]",
                    "{}"
            );
            ConversationEntity conversation = conversationRepository.findById(session.conversationId())
                    .orElseThrow(() -> new BusinessException("CHAT_CONVERSATION_NOT_FOUND", HttpStatus.NOT_FOUND,
                            "conversation not found"));
            conversation.touchLastMessageAt(OffsetDateTime.now());
            String safetyEvents = isSafetyRejection
                    ? ("[{\"type\":\"SAFETY_REJECTED\",\"reason\":\"" + errorMessage + "\",\"timestamp\":\"" + OffsetDateTime.now() + "\"}]")
                    : "[]";
            agentTraceService.record(new AgentTraceService.AgentTraceRecord(
                    session.traceId(),
                    conversation,
                    assistantMessage,
                    "UNKNOWN",
                    "CHAT_ONLY",
                    "[]",
                    "[]",
                    latencyMs,
                    isSafetyRejection ? "SAFETY_REJECTED" : "FAILED",
                    errorMessage,
                    safetyEvents,
                    "LOW",
                    "[]"
            ));
            return conversation.getPublicId().toString();
        });
        if (conversationPublicId != null && ex instanceof BusinessException be
                && "AGENT_SAFETY_REJECTED".equals(be.getCode())) {
            redisContextCacheService.pushMessage(conversationPublicId, "USER", session.userMessage());
            redisContextCacheService.pushMessage(conversationPublicId, "ASSISTANT",
                    "抱歉，你的消息已被安全策略拦截。请调整后重新提问。");
        }
    }

    private UserAccountEntity getCurrentUserAccount() {
        AuthenticatedUser authenticatedUser = currentAuthenticatedUserProvider.getRequiredUser();
        return userAccountRepository.findByPublicId(authenticatedUser.userPublicId())
                .orElseThrow(() -> new BusinessException("USER_NOT_FOUND", HttpStatus.NOT_FOUND,
                        "current user not found"));
    }

    private List<String> splitDeltas(String reply) {
        return List.of(reply.split("(?<=。|\\n)"));
    }

    private void publishErrorAndComplete(SseEmitter emitter, Exception ex) {
        log.error("Chat stream failed", ex);
        try {
            String message = ex instanceof BusinessException ? ex.getMessage() : "chat stream failed";
            streamEventPublisher.publishError(emitter, "CHAT_STREAM_FAILED", message);
            emitter.complete();
        } catch (IOException ioException) {
            emitter.completeWithError(ioException);
        }
    }

    private UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (RuntimeException ex) {
            throw new BusinessException("COMMON_INVALID_PARAM", HttpStatus.BAD_REQUEST,
                    "conversationId must be a valid uuid");
        }
    }

    private String normalizeToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private long elapsedMillis(long startedAt) {
        return Math.max(0, (System.nanoTime() - startedAt) / 1_000_000);
    }

    private record StreamSession(
            Long conversationId,
            String conversationPublicId,
            Long assistantMessageId,
            String assistantMessagePublicId,
            String traceId,
            String userMessage
    ) {
    }

    private record ActiveStreamHandle(
            SseEmitter emitter,
            AtomicBoolean cancelled
    ) {
    }
}
