package com.medora.adk.orchestrator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.adk.runner.Runner;
import com.google.adk.sessions.BaseSessionService;
import com.google.adk.sessions.Session;
import com.google.genai.types.Content;
import com.medora.adk.sse.AdkEventToSseAdapter;
import com.medora.adk.sse.AdkStreamResult;
import com.medora.adk.tool.AdkToolContextResolver;
import com.medora.agent.application.AgentChatCommand;
import com.medora.agent.application.AgentRunResult;
import com.medora.chat.domain.ConversationEntity;
import com.medora.chat.repository.ConversationRepository;
import com.medora.common.metrics.MedoraMetricsService;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Phase 9.7 AdkOrchestrator 单测。
 *
 * Phase 9.7 起,orchestrate(command, emitter, cancelled) 把 Flowable&lt;Event&gt;
 * 委托给 AdkEventToSseAdapter 处理。本测试 mock 掉 adapter,只验证:
 *  - sessionId/userId 解析正确
 *  - 首次/复用 session 行为
 *  - 调用 adapter.stream 时传入正确的 Flowable 和参数
 *  - 用 AdkStreamResult 构造 AgentRunResult
 *  - conversation 不存在 / publicId 为 null fail-fast
 *
 * 真实 Event 翻译逻辑由 AdkEventToSseAdapterTest 覆盖。
 */
class AdkOrchestratorTest {

    private Runner runner;
    private BaseSessionService sessionService;
    private ConversationRepository conversationRepository;
    private AdkEventToSseAdapter sseAdapter;
    private MedoraMetricsService metricsService;
    private AdkOrchestrator orchestrator;

    private static final Long USER_ID = 100L;
    private static final Long CONVERSATION_ID = 200L;
    private static final UUID CONVERSATION_PUBLIC_ID = UUID.randomUUID();
    private static final String TRACE_ID = "trace-test-001";
    private static final String USER_MESSAGE = "我最近咳嗽,应该挂哪个科?";

    @BeforeEach
    void setUp() {
        runner = mock(Runner.class);
        sessionService = mock(BaseSessionService.class);
        conversationRepository = mock(ConversationRepository.class);
        sseAdapter = mock(AdkEventToSseAdapter.class);
        metricsService = mock(MedoraMetricsService.class);
        orchestrator = new AdkOrchestrator(
                runner, sessionService, conversationRepository,
                sseAdapter, new ObjectMapper(), metricsService);

        when(runner.appName()).thenReturn("medora");
        ConversationEntity conversation = mock(ConversationEntity.class);
        when(conversation.getPublicId()).thenReturn(CONVERSATION_PUBLIC_ID);
        when(conversationRepository.findById(CONVERSATION_ID)).thenReturn(Optional.of(conversation));
    }

    private AgentChatCommand command() {
        return new AgentChatCommand(USER_ID, CONVERSATION_ID, 300L, TRACE_ID, USER_MESSAGE);
    }

    private void mockRunnerFlowable() {
        // 注意 ADK Runner.runAsync 参数顺序是 (userId, sessionId, content),不是 (sessionId, userId)
        when(runner.runAsync(eq(String.valueOf(USER_ID)), eq(CONVERSATION_PUBLIC_ID.toString()), any(Content.class)))
                .thenReturn(Flowable.empty());
    }

    private void mockSessionMissing() {
        when(sessionService.getSession(anyString(), anyString(), anyString(), any(Optional.class)))
                .thenReturn(Maybe.empty());
    }

    private void mockSessionExists() {
        Session existing = mock(Session.class);
        when(sessionService.getSession(anyString(), anyString(), anyString(), any(Optional.class)))
                .thenReturn(Maybe.just(existing));
    }

    @Test
    void orchestrateDelegatesToSseAdapterAndBuildsResult() {
        mockSessionMissing();
        when(sessionService.createSession(anyString(), anyString(), any(ConcurrentMap.class), anyString()))
                .thenReturn(Single.just(mock(Session.class)));
        mockRunnerFlowable();
        AdkStreamResult stub = new AdkStreamResult(
                "你好,请稍等。",
                "[{\"toolName\":\"queryDepartments\",\"status\":\"SUCCESS\"}]",
                "[]",
                "LOW",
                List.of("root_router_agent", "triage_agent")
        );
        when(sseAdapter.stream(any(), any(), any())).thenReturn(stub);

        AgentRunResult result = orchestrator.orchestrate(command(), null, new AtomicBoolean(false));

        assertThat(result.reply()).isEqualTo("你好,请稍等。");
        assertThat(result.intentDecision().intentType()).isEqualTo("ADK_ROUTED");
        assertThat(result.hallucinationRisk()).isEqualTo("LOW");
        // toolCallJson 含 ADK 标记和 toolEvents
        assertThat(result.toolCallJson()).contains("\"routeMode\":\"ADK\"");
        assertThat(result.toolCallJson()).contains("queryDepartments");
        assertThat(result.toolCallJson()).contains("root_router_agent");
    }

    @Test
    void orchestrateCreatesSessionWithThreeStateKeysOnFirstCall() {
        mockSessionMissing();
        when(sessionService.createSession(anyString(), anyString(), any(ConcurrentMap.class), anyString()))
                .thenReturn(Single.just(mock(Session.class)));
        mockRunnerFlowable();
        when(sseAdapter.stream(any(), any(), any())).thenReturn(AdkStreamResult.empty());

        orchestrator.orchestrate(command(), null, new AtomicBoolean(false));

        ArgumentCaptor<ConcurrentMap<String, Object>> stateCaptor =
                ArgumentCaptor.forClass(ConcurrentMap.class);
        verify(sessionService).createSession(
                eq("medora"), eq(String.valueOf(USER_ID)), stateCaptor.capture(),
                eq(CONVERSATION_PUBLIC_ID.toString()));

        ConcurrentMap<String, Object> state = stateCaptor.getValue();
        assertThat(state).containsEntry(AdkToolContextResolver.KEY_USER_ACCOUNT_ID, USER_ID);
        assertThat(state).containsEntry(AdkToolContextResolver.KEY_USER_MESSAGE, USER_MESSAGE);
        assertThat(state).containsEntry(AdkToolContextResolver.KEY_TRACE_ID, TRACE_ID);
    }

    @Test
    void orchestrateAlwaysWritesStateKeysEvenWhenSessionExists() {
        // 关键回归:即便 session 已存在,也必须重写 state keys。
        // 早期实现用 "session 已存在就跳过" 的策略,但 PostgresBackedSessionService.getSession
        // 对任何存在的 conversation 都返回非空 Session,导致 state 永远不写,tool 找不到
        // medora:userAccountId 等关键 key(2026-06-23 实测发现的 bug)。
        mockSessionExists();
        when(sessionService.createSession(anyString(), anyString(), any(ConcurrentMap.class), anyString()))
                .thenReturn(Single.just(mock(Session.class)));
        mockRunnerFlowable();
        when(sseAdapter.stream(any(), any(), any())).thenReturn(AdkStreamResult.empty());

        orchestrator.orchestrate(command(), null, new AtomicBoolean(false));

        ArgumentCaptor<ConcurrentMap<String, Object>> stateCaptor =
                ArgumentCaptor.forClass(ConcurrentMap.class);
        verify(sessionService).createSession(
                eq("medora"), eq(String.valueOf(USER_ID)), stateCaptor.capture(),
                eq(CONVERSATION_PUBLIC_ID.toString()));
        assertThat(stateCaptor.getValue())
                .containsEntry(AdkToolContextResolver.KEY_USER_ACCOUNT_ID, USER_ID);
    }

    @Test
    void orchestrateThrowsWhenConversationNotFound() {
        when(conversationRepository.findById(CONVERSATION_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orchestrator.orchestrate(command(), null, new AtomicBoolean(false)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cannot resolve sessionId");
    }

    @Test
    void orchestrateThrowsWhenConversationPublicIdIsNull() {
        ConversationEntity broken = mock(ConversationEntity.class);
        when(broken.getPublicId()).thenReturn(null);
        when(conversationRepository.findById(CONVERSATION_ID)).thenReturn(Optional.of(broken));

        assertThatThrownBy(() -> orchestrator.orchestrate(command(), null, new AtomicBoolean(false)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("publicId is null");
    }

    @Test
    void orchestratePropagatesRunnerException() {
        mockSessionMissing();
        when(sessionService.createSession(anyString(), anyString(), any(ConcurrentMap.class), anyString()))
                .thenReturn(Single.just(mock(Session.class)));
        when(runner.runAsync(anyString(), anyString(), any(Content.class)))
                .thenReturn(Flowable.error(new RuntimeException("simulated ADK failure")));
        // adapter 收到 error Flowable 时透传异常(真实 adapter 行为)
        when(sseAdapter.stream(any(), any(), any()))
                .thenThrow(new RuntimeException("simulated ADK failure"));

        assertThatThrownBy(() -> orchestrator.orchestrate(command(), null, new AtomicBoolean(false)))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("simulated ADK failure");
    }

    @Test
    void orchestratePassesSanitizedUserMessageToRunner() {
        mockSessionMissing();
        when(sessionService.createSession(anyString(), anyString(), any(ConcurrentMap.class), anyString()))
                .thenReturn(Single.just(mock(Session.class)));
        mockRunnerFlowable();
        when(sseAdapter.stream(any(), any(), any())).thenReturn(AdkStreamResult.empty());

        orchestrator.orchestrate(command(), null, new AtomicBoolean(false));

        ArgumentCaptor<Content> contentCaptor = ArgumentCaptor.forClass(Content.class);
        verify(runner).runAsync(anyString(), anyString(), contentCaptor.capture());
        Content captured = contentCaptor.getValue();
        assertThat(captured.parts()).isPresent();
        String text = captured.parts().get().stream()
                .map(com.google.genai.types.Part::text)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .reduce("", String::concat);
        assertThat(text).isEqualTo(USER_MESSAGE);
    }

    @Test
    void orchestrateUsesUuidSessionIdForRunner() {
        mockSessionMissing();
        when(sessionService.createSession(anyString(), anyString(), any(ConcurrentMap.class), anyString()))
                .thenReturn(Single.just(mock(Session.class)));
        mockRunnerFlowable();
        when(sseAdapter.stream(any(), any(), any())).thenReturn(AdkStreamResult.empty());

        orchestrator.orchestrate(command(), null, new AtomicBoolean(false));

        // ADK Runner.runAsync(userId, sessionId, content) — 顺序反了会把 userId 当 sessionId 传
        // 导致 PostgresBackedSessionService.parseSessionId 抛 "sessionId must be a UUID, got: 1"
        verify(runner).runAsync(
                eq(String.valueOf(USER_ID)),
                eq(CONVERSATION_PUBLIC_ID.toString()),
                any(Content.class));
    }

    @Test
    void orchestrateBuildsToolCallJsonWithRouteModeAndTransferChain() {
        mockSessionMissing();
        when(sessionService.createSession(anyString(), anyString(), any(ConcurrentMap.class), anyString()))
                .thenReturn(Single.just(mock(Session.class)));
        mockRunnerFlowable();
        AdkStreamResult stub = new AdkStreamResult(
                "reply",
                "[]",
                "[]",
                "HIGH",
                List.of("root_router_agent", "booking_agent")
        );
        when(sseAdapter.stream(any(), any(), any())).thenReturn(stub);

        AgentRunResult result = orchestrator.orchestrate(command(), null, new AtomicBoolean(false));

        assertThat(result.toolCallJson())
                .contains("\"transferChain\":[\"root_router_agent\",\"booking_agent\"]");
        assertThat(result.hallucinationRisk()).isEqualTo("HIGH");
    }

    /**
     * Phase 9.8 — 验证多跳 transfer 链路正确写入 toolCallJson。
     *
     * 场景:RootRouterAgent 收到"我咳嗽应挂什么科",transfer 到 TriageAgent 做症状分诊,
     * TriageAgent 内部 searchKnowledge 后再 transfer 到 BookingAgent 完成挂号建议。
     * 这种多跳场景比单跳(root → booking)更容易暴露 transferChain 累积 bug。
     */
    @Test
    void orchestrateHandlesMultiHopTransferChainRootToTriageToBooking() {
        mockSessionMissing();
        when(sessionService.createSession(anyString(), anyString(), any(ConcurrentMap.class), anyString()))
                .thenReturn(Single.just(mock(Session.class)));
        mockRunnerFlowable();
        AdkStreamResult stub = new AdkStreamResult(
                "已为您找到呼吸科号源",
                "[{\"toolName\":\"queryDepartments\",\"status\":\"SUCCESS\"},"
                        + "{\"toolName\":\"queryScheduleSlots\",\"status\":\"SUCCESS\"}]",
                "[]",
                "MEDIUM",
                List.of("root_router_agent", "triage_agent", "booking_agent")
        );
        when(sseAdapter.stream(any(), any(), any())).thenReturn(stub);

        AgentRunResult result = orchestrator.orchestrate(command(), null, new AtomicBoolean(false));

        assertThat(result.toolCallJson())
                .contains("\"transferChain\":[\"root_router_agent\",\"triage_agent\",\"booking_agent\"]");
        assertThat(result.toolCallJson()).contains("queryScheduleSlots");
        assertThat(result.hallucinationRisk()).isEqualTo("MEDIUM");
    }

    /**
     * Phase 9.8 — 验证 metrics 正常上报。
     * hallucinationRisk=HIGH + safetyEvents 为空 → recordHallucinationRisk("ADK", "HIGH") + recordSafetyOutcome("ADK", "ALLOW")。
     */
    @Test
    void orchestrateRecordsHallucinationAndSafetyMetrics() {
        mockSessionMissing();
        when(sessionService.createSession(anyString(), anyString(), any(ConcurrentMap.class), anyString()))
                .thenReturn(Single.just(mock(Session.class)));
        mockRunnerFlowable();
        AdkStreamResult stub = new AdkStreamResult(
                "reply",
                "[]",
                "[]",
                "HIGH",
                List.of("root_router_agent", "triage_agent")
        );
        when(sseAdapter.stream(any(), any(), any())).thenReturn(stub);

        orchestrator.orchestrate(command(), null, new AtomicBoolean(false));

        verify(metricsService).recordHallucinationRisk("ADK", "HIGH");
        verify(metricsService).recordSafetyOutcome("ADK", "ALLOW");
    }

    /**
     * Phase 9.8 — 验证 safety 拦截场景下 metrics 上报 BLOCK。
     * safetyEventsJson 非空时表示 LLM callback 触发了安全审查事件(如 inputSafetyBeforeCallback 拒绝)。
     */
    @Test
    void orchestrateRecordsSafetyBlockMetricWhenSafetyEventsPresent() {
        mockSessionMissing();
        when(sessionService.createSession(anyString(), anyString(), any(ConcurrentMap.class), anyString()))
                .thenReturn(Single.just(mock(Session.class)));
        mockRunnerFlowable();
        AdkStreamResult stub = new AdkStreamResult(
                "抱歉,该问题已触发安全策略",
                "[]",
                "[{\"type\":\"SAFETY_BLOCKED\",\"reason\":\"prompt injection detected\"}]",
                "LOW",
                List.of("root_router_agent")
        );
        when(sseAdapter.stream(any(), any(), any())).thenReturn(stub);

        orchestrator.orchestrate(command(), null, new AtomicBoolean(false));

        verify(metricsService).recordSafetyOutcome("ADK", "BLOCK");
        verify(metricsService).recordHallucinationRisk("ADK", "LOW");
    }
}
