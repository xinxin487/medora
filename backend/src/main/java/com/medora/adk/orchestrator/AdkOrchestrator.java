package com.medora.adk.orchestrator;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.adk.events.Event;
import com.google.adk.runner.Runner;
import com.google.adk.sessions.BaseSessionService;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import com.medora.adk.sse.AdkEventToSseAdapter;
import com.medora.adk.sse.AdkStreamResult;
import com.medora.adk.tool.AdkToolContextResolver;
import com.medora.agent.application.AgentChatCommand;
import com.medora.agent.application.AgentRunResult;
import com.medora.agent.application.IntentDecision;
import com.medora.agent.application.RouteMode;
import com.medora.chat.domain.ConversationEntity;
import com.medora.chat.repository.ConversationRepository;
import com.medora.agent.context.AgentContextBundle;
import com.medora.agent.context.ContextBuildRequest;
import com.medora.agent.context.ContextBuilder;
import com.medora.common.config.EvolutionProperties;
import com.medora.common.metrics.MedoraMetricsService;
import com.medora.common.metrics.EvolutionMetrics;
import io.reactivex.rxjava3.core.Flowable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * ADK Orchestrator — Phase 9.8 起为唯一执行路径。
 *
 * 职责:
 *  1) 从 AgentChatCommand 构造 user Content
 *  2) 通过 Runner.runAsync(sessionId, userId, content) 触发 ADK 多 agent 流程
 *  3) 订阅 Flowable&lt;Event&gt;,经 AdkEventToSseAdapter 实时翻译为 SSE 推送
 *  4) 订阅结束后,用 AdkStreamResult 构造 AgentRunResult 返回
 *
 * <p>历史:
 * <ul>
 *   <li>Phase 9.6:同步收集所有 Event(整段一次性推送)</li>
 *   <li>Phase 9.7:改为通过 AdkEventToSseAdapter 逐事件 SSE 推送 + 聚合 trace 字段</li>
 *   <li>Phase 9.8:移除灰度开关,成为唯一 orchestrator</li>
 * </ul>
 *
 * Session 复用:
 *  - sessionId = conversation.publicId(从 Long id 查 ConversationEntity 得到 UUID)
 *  - 每次请求都通过 createSession 写入 per-request state keys
 *    (userAccountId/userMessage/traceId)。详见 ensureSession 注释。
 *
 * 路由策略:本类不重复判断 intent,统一交给 RootRouterAgent 内部 LLM 路由。
 */
@Component
public class AdkOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(AdkOrchestrator.class);

    private final Runner adkRunner;
    private final BaseSessionService sessionService;
    private final ConversationRepository conversationRepository;
    private final AdkEventToSseAdapter sseAdapter;
    private final ObjectMapper objectMapper;
    private final MedoraMetricsService metricsService;
    private final EvolutionProperties evolutionProperties;
    private final ContextBuilder contextBuilder;
    private final EvolutionMetrics evolutionMetrics;

    public AdkOrchestrator(Runner adkRunner,
                           BaseSessionService sessionService,
                           ConversationRepository conversationRepository,
                           AdkEventToSseAdapter sseAdapter,
                           ObjectMapper objectMapper,
                           MedoraMetricsService metricsService) {
        this(adkRunner, sessionService, conversationRepository, sseAdapter, objectMapper, metricsService,
                new EvolutionProperties(false, false, false, false, false), null);
    }

    public AdkOrchestrator(Runner adkRunner,
                           BaseSessionService sessionService,
                           ConversationRepository conversationRepository,
                           AdkEventToSseAdapter sseAdapter,
                           ObjectMapper objectMapper,
                           MedoraMetricsService metricsService,
                           EvolutionProperties evolutionProperties,
                           ContextBuilder contextBuilder) {
        this(adkRunner, sessionService, conversationRepository, sseAdapter, objectMapper, metricsService,
                evolutionProperties, contextBuilder, null);
    }

    @Autowired
    public AdkOrchestrator(Runner adkRunner,
                           BaseSessionService sessionService,
                           ConversationRepository conversationRepository,
                           AdkEventToSseAdapter sseAdapter,
                           ObjectMapper objectMapper,
                           MedoraMetricsService metricsService,
                           EvolutionProperties evolutionProperties,
                           ContextBuilder contextBuilder,
                           EvolutionMetrics evolutionMetrics) {
        this.adkRunner = adkRunner;
        this.sessionService = sessionService;
        this.conversationRepository = conversationRepository;
        this.sseAdapter = sseAdapter;
        this.objectMapper = objectMapper;
        this.metricsService = metricsService;
        this.evolutionProperties = evolutionProperties;
        this.contextBuilder = contextBuilder;
        this.evolutionMetrics = evolutionMetrics;
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
            // 注意:ADK Runner.runAsync 参数顺序是 (userId, sessionId, content),
            // 不是 (sessionId, userId)。sessionId 必须是 UUID 字符串(这里来自 conversation.publicId)。
            Flowable<Event> events = adkRunner.runAsync(userId, sessionId, userContent);
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
        recordRunMetrics(streamResult, elapsed);

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

    /**
     * Phase 9.8 — 把单次 ADK 运行的汇总指标推到 Prometheus。
     *
     * 指标维度:
     *  - hallucination risk level
     *  - 路由深度(transferChain 大小,P50/P95 用 timer 之外的 counter 辅助)
     *  - safety outcome(从 safetyEventsJson 判定 ALLOW/BLOCK)
     *
     * 工具调用粒度的指标在 AdkEventToSseAdapter 里逐事件上报(避免这里再解析一次 JSON)。
     */
    private void recordRunMetrics(AdkStreamResult result, long elapsedMs) {
        try {
            metricsService.recordHallucinationRisk("ADK", result.hallucinationRisk());
            String safetyEvents = result.safetyEventsJson();
            boolean hasSafetyBlock = safetyEvents != null
                    && !safetyEvents.isBlank()
                    && !"[]".equals(safetyEvents);
            metricsService.recordSafetyOutcome("ADK", hasSafetyBlock ? "BLOCK" : "ALLOW");
        } catch (RuntimeException ex) {
            log.debug("metrics recording failed, ignored", ex);
        }
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
     * 每次请求都写入 per-request state keys。
     *
     * 为什么不能 "session 已存在就跳过":PostgresBackedSessionService.getSession 对任何存在的
     * conversation 都返回非空 Session(即使 sessionState 为空)。所以 "skip if exists" 永远生效,
     * state 永远不会写入,导致 ToolExecutionContext 解析失败(tool 找不到 medora:userAccountId)。
     *
     * userMessage / traceId 是 per-request 字段,必须每轮覆盖。userAccountId 同一用户不变,
     * 但每轮重写也无害。createSession 是幂等写入(用新 state 覆盖 conversation.sessionState)。
     */
    private void ensureSession(AgentChatCommand command, String sessionId, String userId) {
        ConcurrentHashMap<String, Object> state = new ConcurrentHashMap<>();
        state.put(AdkToolContextResolver.KEY_USER_ACCOUNT_ID, command.userAccountId());
        state.put(AdkToolContextResolver.KEY_USER_MESSAGE, command.message());
        state.put(AdkToolContextResolver.KEY_TRACE_ID, command.traceId());
        state.put(AdkToolContextResolver.KEY_CONVERSATION_ID, sessionId);
        state.put("context.mode", "LEGACY");
        state.put("context.diagnostics", Map.of());
        if (evolutionProperties.contextBuilder() && contextBuilder != null) {
            try {
                AgentContextBundle bundle = contextBuilder.build(new ContextBuildRequest(
                        command.userAccountId(), command.conversationId(), command.message(), "GENERAL", 8_000));
                state.put("context.profile", bundle.profile().name());
                state.put("context.summary", bundle.summary());
                state.put("context.recent", bundle.recentTurns());
                state.put("context.memories", bundle.memories());
                state.put("context.rag", bundle.ragEvidence());
                state.put("context.business", bundle.businessState());
                boolean hasContext = !bundle.summary().isBlank() || !bundle.recentTurns().isEmpty()
                        || !bundle.memories().isEmpty() || !bundle.ragEvidence().isEmpty()
                        || !bundle.businessState().isEmpty();
                boolean conversationUnavailable = bundle.decisions().stream().anyMatch(decision ->
                        "CONVERSATION".equals(decision.source())
                                && "PROVIDER_UNAVAILABLE".equals(decision.reasonCode()));
                if (hasContext && !conversationUnavailable) {
                    state.put("context.mode", "GOVERNED");
                }
                Map<String, Integer> reasonCounts = new LinkedHashMap<>();
                bundle.decisions().forEach(decision -> reasonCounts.merge(decision.reasonCode(), 1, Integer::sum));
                Map<String, Integer> sourceCounts = new LinkedHashMap<>();
                bundle.decisions().stream().filter(decision -> decision.selected())
                        .forEach(decision -> sourceCounts.merge(decision.source(), 1, Integer::sum));
                int usedTokens = bundle.decisions().stream().filter(decision -> decision.selected())
                        .mapToInt(decision -> decision.estimatedTokens()).sum();
                boolean degraded = reasonCounts.containsKey("PROVIDER_UNAVAILABLE");
                state.put("context.diagnostics", Map.of(
                        "profile", bundle.profile().name(),
                        "budget", Map.of("allocated", bundle.budget().totalAllocated(), "used", usedTokens),
                        "sourceCounts", sourceCounts,
                        "reasonCounts", reasonCounts,
                        "itemFingerprints", bundle.decisions().stream().map(decision -> decision.fingerprint()).filter(value -> value != null && !value.isBlank()).toList(),
                        "decisions", bundle.decisions(),
                        "degraded", degraded));
            } catch (RuntimeException ex) {
                log.warn("context builder degraded. traceId={}, cause={}",
                        command.traceId(), ex.getClass().getSimpleName());
                state.put("context.diagnostics", Map.of("profile", "GENERAL", "degraded", true,
                        "reasonCounts", Map.of("BUILDER_UNAVAILABLE", 1)));
                if (evolutionMetrics != null) {
                    evolutionMetrics.recordContextBuild("GENERAL", "DEGRADED");
                }
            }
        }
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
