package com.medora.chat.service;

import com.medora.auth.domain.UserAccountEntity;
import com.medora.auth.repository.UserAccountRepository;
import com.medora.auth.security.AuthenticatedUser;
import com.medora.auth.security.CurrentAuthenticatedUserProvider;
import com.medora.chat.api.request.ChatStreamRequest;
import com.medora.chat.domain.ChatMessageEntity;
import com.medora.chat.domain.ConversationEntity;
import com.medora.chat.repository.ChatMessageRepository;
import com.medora.chat.repository.ConversationRepository;
import com.medora.chat.sse.StreamEventPublisher;
import com.medora.common.exception.BusinessException;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class ChatRuntimeService {

    private final CurrentAuthenticatedUserProvider currentAuthenticatedUserProvider;
    private final UserAccountRepository userAccountRepository;
    private final ConversationRepository conversationRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final TransactionTemplate transactionTemplate;
    private final TaskExecutor taskExecutor;
    private final StreamEventPublisher streamEventPublisher;

    public ChatRuntimeService(CurrentAuthenticatedUserProvider currentAuthenticatedUserProvider,
                              UserAccountRepository userAccountRepository,
                              ConversationRepository conversationRepository,
                              ChatMessageRepository chatMessageRepository,
                              TransactionTemplate transactionTemplate,
                              TaskExecutor taskExecutor,
                              StreamEventPublisher streamEventPublisher) {
        this.currentAuthenticatedUserProvider = currentAuthenticatedUserProvider;
        this.userAccountRepository = userAccountRepository;
        this.conversationRepository = conversationRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.transactionTemplate = transactionTemplate;
        this.taskExecutor = taskExecutor;
        this.streamEventPublisher = streamEventPublisher;
    }

    public SseEmitter stream(String conversationId, ChatStreamRequest request) {
        Long userAccountId = getCurrentUserAccount().getId();
        SseEmitter emitter = new SseEmitter(60_000L);
        taskExecutor.execute(() -> runStream(emitter, userAccountId, conversationId, request));
        return emitter;
    }

    String buildStubAssistantReply(String userMessage) {
        String normalized = userMessage == null ? "" : userMessage.trim();
        if (normalized.length() > 80) {
            normalized = normalized.substring(0, 80) + "...";
        }
        return "已收到你的问题：“" + normalized
                + "”。当前阶段先完成会话、消息落库和 SSE 流式输出，后续会接入知识库检索与预约工具。";
    }

    private void runStream(SseEmitter emitter, Long userAccountId, String conversationId, ChatStreamRequest request) {
        StreamSession session = null;
        try {
            session = createStreamSession(userAccountId, conversationId, request);
            streamEventPublisher.publishMessageStart(
                    emitter,
                    session.assistantMessagePublicId(),
                    session.conversationPublicId(),
                    session.traceId()
            );

            String reply = buildStubAssistantReply(request.message());
            for (String delta : splitDeltas(reply)) {
                streamEventPublisher.publishDelta(emitter, delta);
            }

            completeAssistantMessage(session.assistantMessageId(), session.conversationId(), reply);
            streamEventPublisher.publishMessageEnd(emitter, session.assistantMessagePublicId());
            emitter.complete();
        } catch (Exception ex) {
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
                    "trace_" + UUID.randomUUID().toString().replace("-", "")
            );
        });
    }

    private void completeAssistantMessage(Long assistantMessageId, Long conversationId, String reply) {
        transactionTemplate.executeWithoutResult(status -> {
            ChatMessageEntity assistantMessage = chatMessageRepository.findById(assistantMessageId)
                    .orElseThrow(() -> new BusinessException("CHAT_MESSAGE_NOT_FOUND", HttpStatus.NOT_FOUND,
                            "assistant message not found"));
            assistantMessage.completeAssistantContent(reply, "{\"inputTokens\":0,\"outputTokens\":0}");
            ConversationEntity conversation = conversationRepository.findById(conversationId)
                    .orElseThrow(() -> new BusinessException("CHAT_CONVERSATION_NOT_FOUND", HttpStatus.NOT_FOUND,
                            "conversation not found"));
            conversation.touchLastMessageAt(OffsetDateTime.now());
        });
    }

    private UserAccountEntity getCurrentUserAccount() {
        AuthenticatedUser authenticatedUser = currentAuthenticatedUserProvider.getRequiredUser();
        return userAccountRepository.findByPublicId(authenticatedUser.userPublicId())
                .orElseThrow(() -> new BusinessException("USER_NOT_FOUND", HttpStatus.NOT_FOUND,
                        "current user not found"));
    }

    private List<String> splitDeltas(String reply) {
        return List.of(reply.split("(?<=。)"));
    }

    private void publishErrorAndComplete(SseEmitter emitter, Exception ex) {
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

    private record StreamSession(
            Long conversationId,
            String conversationPublicId,
            Long assistantMessageId,
            String assistantMessagePublicId,
            String traceId
    ) {
    }
}
