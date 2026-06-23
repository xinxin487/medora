package com.medora.adk.orchestrator;

import com.google.adk.events.Event;
import com.google.adk.runner.Runner;
import com.google.adk.sessions.BaseSessionService;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import com.medora.adk.config.AdkProperties;
import com.medora.adk.tool.AdkToolContextResolver;
import com.medora.agent.application.AgentChatCommand;
import com.medora.agent.application.AgentRunResult;
import com.medora.agent.application.IntentDecision;
import com.medora.agent.application.RouteMode;
import com.medora.chat.domain.ConversationEntity;
import com.medora.chat.repository.ConversationRepository;
import io.reactivex.rxjava3.core.Flowable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * ADK Orchestrator — 灰度接入路径。
 *
 * 职责:
 *  1) 从 AgentChatCommand 构造 user Content
 *  2) 通过 Runner.runAsync(sessionId, userId, content) 触发 ADK 多 agent 流程
 *  3) 收集所有 Event 的 text part,拼成最终回复
 *  4) 把回复包装为 AgentRunResult 返回(对 ChatOrchestrator 调用方透明)
 *
 * Phase 9.6:同步收集所有 Event,Phase 9.7 改为 Flowable 订阅 + SSE adapter。
 *
 * Session 复用:
 *  - sessionId = conversation.publicId(从 Long id 查 ConversationEntity 得到 UUID)
 *  - 首次进入时,PostgresBackedSessionService.getSession 返回 Maybe.empty,
 *    由本类显式 createSession 写入 userAccountId/userMessage/traceId 三个 state key
 *  - 后续轮次 getSession 命中,state 跨轮保持
 *
 * 灰度策略:
 *  ChatOrchestrator 入口判断 adkProperties.enabled,委托给本类。具体 intent 过滤
 *  (TRIAGE only 还是全 intent)由 ChatOrchestrator 决定,本类不重复判断。
 */
@Component
@ConditionalOnProperty(prefix = "medora.adk", name = "enabled", havingValue = "true")
public class AdkOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(AdkOrchestrator.class);

    private final Runner adkRunner;
    private final BaseSessionService sessionService;
    private final AdkProperties adkProperties;
    private final ConversationRepository conversationRepository;

    public AdkOrchestrator(Runner adkRunner,
                           BaseSessionService sessionService,
                           AdkProperties adkProperties,
                           ConversationRepository conversationRepository) {
        this.adkRunner = adkRunner;
        this.sessionService = sessionService;
        this.adkProperties = adkProperties;
        this.conversationRepository = conversationRepository;
    }

    public AgentRunResult orchestrate(AgentChatCommand command) {
        String sessionId = resolveSessionId(command.conversationId());
        String userId = String.valueOf(command.userAccountId());
        ensureSession(command, sessionId, userId);

        Content userContent = Content.builder()
                .role("user")
                .parts(Part.fromText(command.message()))
                .build();

        long startMs = System.currentTimeMillis();
        AtomicReference<String> finalReply = new AtomicReference<>("");
        try {
            Flowable<Event> events = adkRunner.runAsync(sessionId, userId, userContent);
            StringBuilder text = new StringBuilder();
            events.blockingForEach(event -> {
                String part = extractText(event);
                if (part != null && !part.isEmpty()) {
                    text.append(part);
                }
            });
            finalReply.set(text.toString());
        } catch (RuntimeException ex) {
            log.error("ADK runAsync failed. conversationId={}, traceId={}",
                    command.conversationId(), command.traceId(), ex);
            throw ex;
        }
        long elapsed = System.currentTimeMillis() - startMs;
        log.info("ADK orchestrate completed. conversationId={}, traceId={}, elapsedMs={}, replyLen={}",
                command.conversationId(), command.traceId(), elapsed, finalReply.get().length());

        // Phase 9.6 阶段:返回最小化的 AgentRunResult。
        // intentDecision/riskFactors 等字段留空,9.7 SSE adapter 完善后再补。
        IntentDecision decision = new IntentDecision(
                "ADK_ROUTED",
                RouteMode.CHAT_ONLY,
                false,
                List.of()
        );
        return new AgentRunResult(
                decision,
                finalReply.get(),
                List.of(),
                List.of(),
                "[]",
                "{\"routeMode\":\"ADK\"}",
                "{}",
                "[]",
                "LOW",
                "[]"
        );
    }

    /**
     * 首次进入会话时创建 session 并注入 ToolExecutionContext 所需的三个 state key。
     * 后续轮次 session 已存在,跳过创建。
     */
    private void ensureSession(AgentChatCommand command, String sessionId, String userId) {
        Object existing = sessionService.getSession(
                adkRunner.appName(), userId, sessionId, java.util.Optional.empty()).blockingGet();
        if (existing != null) {
            return;
        }
        ConcurrentHashMap<String, Object> state = new ConcurrentHashMap<>();
        state.put(AdkToolContextResolver.KEY_USER_ACCOUNT_ID, command.userAccountId());
        state.put(AdkToolContextResolver.KEY_USER_MESSAGE, command.message());
        state.put(AdkToolContextResolver.KEY_TRACE_ID, command.traceId());
        sessionService.createSession(adkRunner.appName(), userId, state, sessionId)
                .blockingGet();
    }

    private String resolveSessionId(Long conversationId) {
        ConversationEntity entity = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new IllegalStateException(
                        "cannot resolve sessionId: conversation not found, id=" + conversationId));
        UUID publicId = entity.getPublicId();
        if (publicId == null) {
            throw new IllegalStateException(
                    "conversation publicId is null, id=" + conversationId);
        }
        return publicId.toString();
    }

    private static String extractText(Event event) {
        return event.content()
                .flatMap(Content::parts)
                .map(parts -> parts.stream()
                        .map(Part::text)
                        .filter(java.util.Optional::isPresent)
                        .map(java.util.Optional::get)
                        .reduce("", String::concat))
                .orElse("");
    }

    /** 调试用 — 用现有 conversationId 跑一次性测试 */
    public AgentRunResult orchestrateOneShot(Long userAccountId, Long conversationId, String message) {
        AgentChatCommand command = new AgentChatCommand(
                userAccountId,
                conversationId,
                0L,
                "trace-oneshot",
                message
        );
        return orchestrate(command);
    }
}
