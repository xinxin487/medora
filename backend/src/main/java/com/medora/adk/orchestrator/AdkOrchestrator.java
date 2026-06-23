package com.medora.adk.orchestrator;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.adk.events.Event;
import com.google.adk.runner.Runner;
import com.google.adk.sessions.BaseSessionService;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import com.medora.adk.config.AdkProperties;
import com.medora.adk.sse.AdkEventToSseAdapter;
import com.medora.adk.sse.AdkStreamResult;
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
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * ADK Orchestrator — 灰度接入路径。
 *
 * 职责:
 *  1) 从 AgentChatCommand 构造 user Content
 *  2) 通过 Runner.runAsync(sessionId, userId, content) 触发 ADK 多 agent 流程
 *  3) 订阅 Flowable&lt;Event&gt;,经 AdkEventToSseAdapter 实时翻译为 SSE 推送
 *  4) 订阅结束后,用 AdkStreamResult 构造 AgentRunResult 返回
 *
 * Phase 9.6:同步收集所有 Event(整段一次性推送)
 * Phase 9.7:改为通过 AdkEventToSseAdapter 逐事件 SSE 推送 + 聚合 trace 字段
 *
 * Session 复用:
 *  - sessionId = conversation.publicId(从 Long id 查 ConversationEntity 得到 UUID)
 *  - 首次进入时,PostgresBackedSessionService.getSession 返回 Maybe.empty,
 *    由本类显式 createSession 写入 userAccountId/userMessage/traceId 三个 state key
 *  - 后续轮次 getSession 命中,state 跨轮保持
 *
 * 灰度策略:
 *  ChatOrchestrator 入口判断 adkProperties.enabled,委托给本类。
 *  本类不重复判断 intent,统一交给 RootRouterAgent 内部 LLM 路由。
 */
@Component
@ConditionalOnProperty(prefix = "medora.adk", name = "enabled", havingValue = "true")
public class AdkOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(AdkOrchestrator.class);

    private final Runner adkRunner;
    private final BaseSessionService sessionService;
    private final AdkProperties adkProperties;
    private final ConversationRepository conversationRepository;
    private final AdkEventToSseAdapter sseAdapter;
    private final ObjectMapper objectMapper;

    public AdkOrchestrator(Runner adkRunner,
                           BaseSessionService sessionService,
                           AdkProperties adkProperties,
                           ConversationRepository conversationRepository,
                           AdkEventToSseAdapter sseAdapter,
                           ObjectMapper objectMapper) {
        this.adkRunner = adkRunner;
        this.sessionService = sessionService;
        this.adkProperties = adkProperties;
        this.conversationRepository = conversationRepository;
        this.sseAdapter = sseAdapter;
        this.objectMapper = objectMapper;
    }

    /**
     * Phase 9.7 主入口 — 接收 SseEmitter 和 cancelled,实时推送 SSE。
     */
    public AgentRunResult orchestrate(AgentChatCommand command,
                                      SseEmitter emitter,
                                      AtomicBoolean cancelled) {
        String sessionId = resolveSessionId(command.conversationId());
        String userId = String.valueOf(command.userAccountId());
        ensureSession(command, sessionId, userId);

        Content userContent = Content.builder()
                .role("user")
                .parts(Part.fromText(command.message()))
                .build();

        long startMs = System.currentTimeMillis();
        AdkStreamResult streamResult;
        try {
            Flowable<Event> events = adkRunner.runAsync(sessionId, userId, userContent);
            streamResult = sseAdapter.stream(events, emitter, cancelled);
        } catch (RuntimeException ex) {
            log.error("ADK runAsync failed. conversationId={}, traceId={}",
                    command.conversationId(), command.traceId(), ex);
            throw ex;
        }
        long elapsed = System.currentTimeMillis() - startMs;
        log.info("ADK orchestrate completed. conversationId={}, traceId={}, elapsedMs={}, replyLen={}, toolEventsLen={}",
                command.conversationId(), command.traceId(), elapsed,
                streamResult.finalReply().length(),
                streamResult.toolEventsJson().length());

        IntentDecision decision = new IntentDecision(
                "ADK_ROUTED",
                RouteMode.CHAT_ONLY,
                false,
                List.of()
        );
        return new AgentRunResult(
                decision,
                streamResult.finalReply(),
                List.of(),
                List.of(),
                "[]",
                buildToolCallJson(streamResult),
                "{}",
                streamResult.safetyEventsJson(),
                streamResult.hallucinationRisk(),
                "[]"
        );
    }

    private String buildToolCallJson(AdkStreamResult result) {
        // toolCallJson 字段保持与旧路径一致的 schema:{routeMode, transferChain, toolEvents}
        try {
            List<Map<String, Object>> toolEvents = objectMapper.readValue(
                    result.toolEventsJson(),
                    new TypeReference<>() {}
            );
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("routeMode", "ADK");
            payload.put("transferChain", result.transferChain());
            payload.put("toolEvents", toolEvents);
            return objectMapper.writeValueAsString(payload);
        } catch (Exception ex) {
            log.warn("failed to build toolCallJson, falling back to minimal marker", ex);
            return "{\"routeMode\":\"ADK\"}";
        }
    }

    /**
     * 首次进入会话时创建 session 并注入 ToolExecutionContext 所需的三个 state key。
     * 后续轮次 session 已存在,跳过创建。
     */
    private void ensureSession(AgentChatCommand command, String sessionId, String userId) {
        Object existing = sessionService.getSession(
                adkRunner.appName(), userId, sessionId, Optional.empty()).blockingGet();
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

    /** 调试用 — 用现有 conversationId 跑一次性测试(无 SSE,只返回结果) */
    public AgentRunResult orchestrateOneShot(Long userAccountId, Long conversationId, String message) {
        AgentChatCommand command = new AgentChatCommand(
                userAccountId,
                conversationId,
                0L,
                "trace-oneshot",
                message
        );
        return orchestrate(command, null, new AtomicBoolean(false));
    }
}
