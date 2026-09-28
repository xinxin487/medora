package com.medora.adk.orchestrator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.adk.runner.Runner;
import com.google.adk.sessions.BaseSessionService;
import com.google.adk.sessions.Session;
import com.google.adk.sessions.State;
import com.google.adk.agents.CallbackContext;
import com.google.adk.models.LlmRequest;
import com.medora.adk.context.ContextPromptBeforeCallback;
import com.medora.adk.sse.AdkEventToSseAdapter;
import com.medora.adk.sse.AdkStreamResult;
import com.medora.agent.context.*;
import com.medora.agent.application.AgentChatCommand;
import com.medora.chat.domain.ConversationEntity;
import com.medora.chat.repository.ConversationRepository;
import com.medora.common.config.EvolutionProperties;
import com.medora.common.metrics.MedoraMetricsService;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AdkContextInjectionTest {
    @Test void flagOnWritesGovernedContextStateKeys() {
        Runner runner = mock(Runner.class); BaseSessionService sessions = mock(BaseSessionService.class);
        ConversationRepository conversations = mock(ConversationRepository.class); AdkEventToSseAdapter adapter = mock(AdkEventToSseAdapter.class);
        ContextBuilder builder = mock(ContextBuilder.class); ConversationEntity conversation = mock(ConversationEntity.class); UUID id = UUID.randomUUID();
        when(conversation.getPublicId()).thenReturn(id); when(conversations.findById(2L)).thenReturn(Optional.of(conversation)); when(runner.appName()).thenReturn("medora");
        when(builder.build(any())).thenReturn(new AgentContextBundle(ContextProfile.BOOKING, new ContextBudget(1,1,1,1,1,1), "预约", "摘要", List.of("最近"), List.of("记忆"), List.of("证据"), Map.of("slotId", "s1"), List.of()));
        when(sessions.createSession(anyString(), anyString(), any(ConcurrentMap.class), anyString())).thenReturn(Single.just(mock(Session.class)));
        when(runner.runAsync(anyString(), anyString(), any())).thenReturn(Flowable.empty()); when(adapter.stream(any(), any(), any())).thenReturn(AdkStreamResult.empty());
        var orchestrator = new AdkOrchestrator(runner, sessions, conversations, adapter, new ObjectMapper(), mock(MedoraMetricsService.class), new EvolutionProperties(true,false,false,false,false), builder);
        orchestrator.orchestrate(new AgentChatCommand(1L,2L,3L,"trace","预约"), null, new AtomicBoolean());
        ArgumentCaptor<ConcurrentMap<String,Object>> captor = ArgumentCaptor.forClass(ConcurrentMap.class);
        verify(sessions).createSession(anyString(), anyString(), captor.capture(), eq(id.toString()));
        assertThat(captor.getValue()).containsKeys("context.profile", "context.summary", "context.recent", "context.memories", "context.rag", "context.business");
        assertThat(captor.getValue()).containsEntry("context.mode", "GOVERNED");
    }


    @Test void flagOffLeavesLegacyHistoryPathActiveAndDoesNotBuildContext() {
        Runner runner = mock(Runner.class);
        BaseSessionService sessions = mock(BaseSessionService.class);
        ConversationRepository conversations = mock(ConversationRepository.class);
        AdkEventToSseAdapter adapter = mock(AdkEventToSseAdapter.class);
        ContextBuilder builder = mock(ContextBuilder.class);
        ConversationEntity conversation = mock(ConversationEntity.class);
        UUID id = UUID.randomUUID();
        when(conversation.getPublicId()).thenReturn(id);
        when(conversations.findById(2L)).thenReturn(Optional.of(conversation));
        when(runner.appName()).thenReturn("medora");
        when(sessions.createSession(anyString(), anyString(), any(ConcurrentMap.class), anyString()))
                .thenReturn(Single.just(mock(Session.class)));
        when(runner.runAsync(anyString(), anyString(), any())).thenReturn(Flowable.empty());
        when(adapter.stream(any(), any(), any())).thenReturn(AdkStreamResult.empty());
        AdkOrchestrator orchestrator = new AdkOrchestrator(runner, sessions, conversations, adapter,
                new ObjectMapper(), mock(MedoraMetricsService.class),
                new EvolutionProperties(false, false, false, false, false), builder);

        orchestrator.orchestrate(new AgentChatCommand(1L, 2L, 3L, "trace", "预约"),
                null, new AtomicBoolean());

        ArgumentCaptor<ConcurrentMap<String, Object>> captured = ArgumentCaptor.forClass(ConcurrentMap.class);
        verify(sessions).createSession(anyString(), anyString(), captured.capture(), eq(id.toString()));
        assertThat(captured.getValue()).containsEntry("context.mode", "LEGACY");
        verifyNoInteractions(builder);
    }

    @Test void builderFailureFallsBackToLegacyAndRecordsSanitizedDegradation() {
        Runner runner = mock(Runner.class);
        BaseSessionService sessions = mock(BaseSessionService.class);
        ConversationRepository conversations = mock(ConversationRepository.class);
        AdkEventToSseAdapter adapter = mock(AdkEventToSseAdapter.class);
        ContextBuilder builder = mock(ContextBuilder.class);
        ConversationEntity conversation = mock(ConversationEntity.class);
        UUID id = UUID.randomUUID();
        when(conversation.getPublicId()).thenReturn(id);
        when(conversations.findById(2L)).thenReturn(Optional.of(conversation));
        when(runner.appName()).thenReturn("medora");
        when(builder.build(any())).thenThrow(new IllegalStateException("private provider detail"));
        when(sessions.createSession(anyString(), anyString(), any(ConcurrentMap.class), anyString()))
                .thenReturn(Single.just(mock(Session.class)));
        when(runner.runAsync(anyString(), anyString(), any())).thenReturn(Flowable.empty());
        when(adapter.stream(any(), any(), any())).thenReturn(AdkStreamResult.empty());
        AdkOrchestrator orchestrator = new AdkOrchestrator(runner, sessions, conversations, adapter,
                new ObjectMapper(), mock(MedoraMetricsService.class),
                new EvolutionProperties(true, false, false, false, false), builder);

        orchestrator.orchestrate(new AgentChatCommand(1L, 2L, 3L, "trace", "预约"),
                null, new AtomicBoolean());

        ArgumentCaptor<ConcurrentMap<String, Object>> captured = ArgumentCaptor.forClass(ConcurrentMap.class);
        verify(sessions).createSession(anyString(), anyString(), captured.capture(), eq(id.toString()));
        assertThat(captured.getValue()).containsEntry("context.mode", "LEGACY");
        assertThat(captured.getValue().get("context.diagnostics").toString())
                .contains("degraded=true").doesNotContain("private provider detail");
    }

    @Test void conversationProviderFailureKeepsLegacyTurnsEvenIfMemorySucceeds() {
        Runner runner = mock(Runner.class);
        BaseSessionService sessions = mock(BaseSessionService.class);
        ConversationRepository conversations = mock(ConversationRepository.class);
        AdkEventToSseAdapter adapter = mock(AdkEventToSseAdapter.class);
        ContextBuilder builder = mock(ContextBuilder.class);
        ConversationEntity conversation = mock(ConversationEntity.class);
        UUID id = UUID.randomUUID();
        when(conversation.getPublicId()).thenReturn(id);
        when(conversations.findById(2L)).thenReturn(Optional.of(conversation));
        when(runner.appName()).thenReturn("medora");
        when(builder.build(any())).thenReturn(new AgentContextBundle(ContextProfile.GENERAL,
                new ContextBudget(200, 250, 100, 300, 100, 50), "问题", "", List.of(),
                List.of("用户偏好上午"), List.of(), Map.of(),
                List.of(new ContextDecision("CONVERSATION", "", 0, false, "PROVIDER_UNAVAILABLE"))));
        when(sessions.createSession(anyString(), anyString(), any(ConcurrentMap.class), anyString()))
                .thenReturn(Single.just(mock(Session.class)));
        when(runner.runAsync(anyString(), anyString(), any())).thenReturn(Flowable.empty());
        when(adapter.stream(any(), any(), any())).thenReturn(AdkStreamResult.empty());
        AdkOrchestrator orchestrator = new AdkOrchestrator(runner, sessions, conversations, adapter,
                new ObjectMapper(), mock(MedoraMetricsService.class),
                new EvolutionProperties(true, false, false, false, false), builder);

        orchestrator.orchestrate(new AgentChatCommand(1L, 2L, 3L, "trace", "问题"), null, new AtomicBoolean());

        ArgumentCaptor<ConcurrentMap<String, Object>> captured = ArgumentCaptor.forClass(ConcurrentMap.class);
        verify(sessions).createSession(anyString(), anyString(), captured.capture(), eq(id.toString()));
        assertThat(captured.getValue()).containsEntry("context.mode", "LEGACY");
        assertThat(captured.getValue().get("context.diagnostics").toString()).contains("degraded=true");
    }

    @Test void realBuilderOutputReachesTheModelInstructions() {
        Runner runner = mock(Runner.class);
        BaseSessionService sessions = mock(BaseSessionService.class);
        ConversationRepository conversations = mock(ConversationRepository.class);
        AdkEventToSseAdapter adapter = mock(AdkEventToSseAdapter.class);
        ConversationEntity conversation = mock(ConversationEntity.class);
        UUID id = UUID.randomUUID();
        when(conversation.getPublicId()).thenReturn(id);
        when(conversations.findById(2L)).thenReturn(Optional.of(conversation));
        when(runner.appName()).thenReturn("medora");
        when(sessions.createSession(anyString(), anyString(), any(ConcurrentMap.class), anyString()))
                .thenReturn(Single.just(mock(Session.class)));
        when(runner.runAsync(anyString(), anyString(), any())).thenReturn(Flowable.empty());
        when(adapter.stream(any(), any(), any())).thenReturn(AdkStreamResult.empty());
        ContextProvider provider = request -> new ContextProvider.Result("CONVERSATION", List.of(
                new ContextItem("SUMMARY", "ROLLING", "hash-only", "用户自述对青霉素过敏",
                        1.0, "CONVERSATION", null, 12)), Map.of());
        ContextBuilder realBuilder = new ContextBuilder(new ContextProfileResolver(),
                new TokenBudgetAllocator(), List.of(provider));
        AdkOrchestrator orchestrator = new AdkOrchestrator(runner, sessions, conversations, adapter,
                new ObjectMapper(), mock(MedoraMetricsService.class),
                new EvolutionProperties(true, false, false, false, false), realBuilder);

        orchestrator.orchestrate(new AgentChatCommand(1L, 2L, 3L, "trace", "下一步呢"),
                null, new AtomicBoolean());

        ArgumentCaptor<ConcurrentMap<String, Object>> captured = ArgumentCaptor.forClass(ConcurrentMap.class);
        verify(sessions).createSession(anyString(), anyString(), captured.capture(), eq(id.toString()));
        CallbackContext callback = mock(CallbackContext.class);
        doReturn(new State(new ConcurrentHashMap<>(captured.getValue()))).when(callback).state();
        LlmRequest.Builder modelRequest = LlmRequest.builder();
        new ContextPromptBeforeCallback().call(callback, modelRequest);
        assertThat(modelRequest.build().getSystemInstructions())
                .anyMatch(instruction -> instruction.contains("用户自述对青霉素过敏"));
    }
}
