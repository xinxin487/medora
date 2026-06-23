package com.medora.adk.orchestrator;

import com.google.adk.events.Event;
import com.google.adk.runner.Runner;
import com.google.adk.sessions.BaseSessionService;
import com.google.adk.sessions.Session;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import com.medora.adk.config.AdkProperties;
import com.medora.adk.tool.AdkToolContextResolver;
import com.medora.agent.application.AgentChatCommand;
import com.medora.agent.application.AgentRunResult;
import com.medora.chat.domain.ConversationEntity;
import com.medora.chat.repository.ConversationRepository;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Phase 9.6 AdkOrchestrator 单测。
 *
 * 不调用真实 LLM — Runner.runAsync 用 mock Flowable 返回预设 Event 序列。
 * 验证:
 *  - orchestrate() 把 Event 的 text part 拼成最终回复,返回带 ADK 标记的 AgentRunResult
 *  - ensureSession 首次创建 session 时注入三个 state key
 *  - ensureSession 已存在 session 时跳过创建
 *  - resolveSessionId 找不到 conversation 时 fail-fast
 *  - Runner 抛异常时,orchestrate 透传异常
 */
class AdkOrchestratorTest {

    private Runner runner;
    private BaseSessionService sessionService;
    private AdkProperties adkProperties;
    private ConversationRepository conversationRepository;
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
        adkProperties = new AdkProperties();
        adkProperties.setEnabled(true);
        conversationRepository = mock(ConversationRepository.class);
        orchestrator = new AdkOrchestrator(
                runner, sessionService, adkProperties, conversationRepository);

        when(runner.appName()).thenReturn("medora");
        ConversationEntity conversation = mock(ConversationEntity.class);
        when(conversation.getPublicId()).thenReturn(CONVERSATION_PUBLIC_ID);
        when(conversationRepository.findById(CONVERSATION_ID)).thenReturn(Optional.of(conversation));
    }

    private AgentChatCommand command() {
        return new AgentChatCommand(USER_ID, CONVERSATION_ID, 300L, TRACE_ID, USER_MESSAGE);
    }

    /** 模拟 Runner 返回若干 Event,每个带一段 text part */
    private void mockRunnerEvents(String... textParts) {
        List<Event> events = java.util.Arrays.stream(textParts)
                .map(text -> Event.builder()
                        .id("evt-" + System.nanoTime())
                        .content(Content.builder()
                                .role("model")
                                .parts(Part.fromText(text))
                                .build())
                        .build())
                .toList();
        when(runner.runAsync(eq(CONVERSATION_PUBLIC_ID.toString()), eq(String.valueOf(USER_ID)), any(Content.class)))
                .thenReturn(Flowable.fromIterable(events));
    }

    /** 模拟 getSession 返回 empty(首次进入) */
    private void mockSessionMissing() {
        when(sessionService.getSession(anyString(), anyString(), anyString(), any(Optional.class)))
                .thenReturn(Maybe.empty());
    }

    /** 模拟 getSession 返回已有 session(后续轮次) */
    private void mockSessionExists() {
        Session existing = mock(Session.class);
        when(sessionService.getSession(anyString(), anyString(), anyString(), any(Optional.class)))
                .thenReturn(Maybe.just(existing));
    }

    @Test
    void orchestrateCollectsTextFromEventsAndReturnsAdkMarker() {
        mockSessionMissing();
        when(sessionService.createSession(anyString(), anyString(), any(ConcurrentMap.class), anyString()))
                .thenReturn(Single.just(mock(Session.class)));
        mockRunnerEvents("你好,", "我帮你查一下呼吸科。");

        AgentRunResult result = orchestrator.orchestrate(command());

        assertThat(result.reply()).isEqualTo("你好,我帮你查一下呼吸科。");
        assertThat(result.toolCallJson()).contains("\"routeMode\":\"ADK\"");
        assertThat(result.intentDecision().intentType()).isEqualTo("ADK_ROUTED");
    }

    @Test
    void orchestrateCreatesSessionWithThreeStateKeysOnFirstCall() {
        mockSessionMissing();
        when(sessionService.createSession(anyString(), anyString(), any(ConcurrentMap.class), anyString()))
                .thenReturn(Single.just(mock(Session.class)));
        mockRunnerEvents("ok");

        orchestrator.orchestrate(command());

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
    void orchestrateSkipsSessionCreationWhenSessionExists() {
        mockSessionExists();
        mockRunnerEvents("ok");

        orchestrator.orchestrate(command());

        verify(sessionService, never())
                .createSession(anyString(), anyString(), any(ConcurrentMap.class), anyString());
    }

    @Test
    void orchestrateThrowsWhenConversationNotFound() {
        when(conversationRepository.findById(CONVERSATION_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orchestrator.orchestrate(command()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cannot resolve sessionId");
    }

    @Test
    void orchestrateThrowsWhenConversationPublicIdIsNull() {
        ConversationEntity broken = mock(ConversationEntity.class);
        when(broken.getPublicId()).thenReturn(null);
        when(conversationRepository.findById(CONVERSATION_ID)).thenReturn(Optional.of(broken));

        assertThatThrownBy(() -> orchestrator.orchestrate(command()))
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

        assertThatThrownBy(() -> orchestrator.orchestrate(command()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("simulated ADK failure");
    }

    @Test
    void orchestrateReturnsEmptyReplyWhenEventsHaveNoText() {
        mockSessionMissing();
        when(sessionService.createSession(anyString(), anyString(), any(ConcurrentMap.class), anyString()))
                .thenReturn(Single.just(mock(Session.class)));
        // 空 part list (no text)
        Event textlessEvent = Event.builder()
                .id("evt-empty")
                .content(Content.builder().role("model").build())
                .build();
        when(runner.runAsync(anyString(), anyString(), any(Content.class)))
                .thenReturn(Flowable.just(textlessEvent));

        AgentRunResult result = orchestrator.orchestrate(command());

        assertThat(result.reply()).isEmpty();
        // 仍然有 ADK 标记
        assertThat(result.toolCallJson()).contains("\"routeMode\":\"ADK\"");
    }

    @Test
    void orchestratePassesUuidSessionIdToRunner() {
        mockSessionMissing();
        when(sessionService.createSession(anyString(), anyString(), any(ConcurrentMap.class), anyString()))
                .thenReturn(Single.just(mock(Session.class)));
        mockRunnerEvents("ok");

        orchestrator.orchestrate(command());

        // Runner 收到的 sessionId 必须是 UUID 字符串(publicId),而非 Long id
        verify(runner).runAsync(
                eq(CONVERSATION_PUBLIC_ID.toString()),
                eq(String.valueOf(USER_ID)),
                any(Content.class));
    }

    @Test
    void orchestratePassesSanitizedUserMessageToRunner() {
        mockSessionMissing();
        when(sessionService.createSession(anyString(), anyString(), any(ConcurrentMap.class), anyString()))
                .thenReturn(Single.just(mock(Session.class)));
        mockRunnerEvents("ok");

        orchestrator.orchestrate(command());

        ArgumentCaptor<Content> contentCaptor = ArgumentCaptor.forClass(Content.class);
        verify(runner).runAsync(anyString(), anyString(), contentCaptor.capture());
        Content captured = contentCaptor.getValue();
        assertThat(captured.parts()).isPresent();
        String text = captured.parts().get().stream()
                .map(Part::text)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .reduce("", String::concat);
        assertThat(text).isEqualTo(USER_MESSAGE);
    }

    @Test
    void orchestrateUsesConstantAppNameMedora() {
        mockSessionMissing();
        when(sessionService.createSession(anyString(), anyString(), any(ConcurrentMap.class), anyString()))
                .thenReturn(Single.just(mock(Session.class)));
        mockRunnerEvents("ok");

        orchestrator.orchestrate(command());

        verify(sessionService).getSession(
                eq("medora"), anyString(), anyString(), any(Optional.class));
        verify(sessionService).createSession(
                eq("medora"), anyString(), any(ConcurrentMap.class), anyString());
    }
}
