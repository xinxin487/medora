package com.medora.eval.runner;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medora.adk.orchestrator.AdkOrchestrator;
import com.medora.agent.application.AgentRunResult;
import com.medora.agent.application.IntentDecision;
import com.medora.agent.application.RouteMode;
import com.medora.auth.domain.UserAccountEntity;
import com.medora.auth.repository.UserAccountRepository;
import com.medora.chat.domain.ConversationEntity;
import com.medora.chat.repository.ConversationRepository;
import com.medora.eval.domain.EvalRun;
import com.medora.eval.domain.TestCase;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Phase 10 W4.5 — OrchestrateOneShotHarnessHelper 单测。
 *
 * <p>用 mock 注入 AdkOrchestrator / ConversationRepository / UserAccountRepository,
 * 验证 helper 把 AgentRunResult 正确映射到 ActualBehavior。
 *
 * <p>不验证真实 ADK / DB 行为 — 那是集成测试的范畴,需要 SpringBootTest。
 */
class OrchestrateOneShotHarnessHelperTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void mapsReplyTransferChainAndToolEvents() {
        AdkOrchestrator orch = mock(AdkOrchestrator.class);
        when(orch.orchestrateOneShot(anyLong(), anyLong(), anyString()))
                .thenReturn(stubResult(
                        "建议挂神经内科",
                        "{\"routeMode\":\"ADK\",\"transferChain\":[\"root_router_agent\",\"triage_agent\"],\"toolEvents\":[{\"toolName\":\"queryScheduleSlots\",\"status\":\"SUCCESS\"}]}"
                ));

        ConversationRepository repo = mock(ConversationRepository.class);
        when(repo.save(any(ConversationEntity.class))).thenAnswer(inv -> assignId(inv.getArgument(0), 42L));

        UserAccountRepository userRepo = mock(UserAccountRepository.class);
        when(userRepo.findById(1L)).thenReturn(Optional.of(new UserAccountEntity()));

        TestCase tc = new TestCase(
                "c1", TestCase.Category.ROUTING, "我头痛",
                new TestCase.ContextPreset(1L, null, List.of()),
                new TestCase.Expectations(
                        new TestCase.RouteExpectation(List.of("root_router_agent", "triage_agent")),
                        List.of(),
                        List.of(),
                        "ALLOW"
                ),
                TestCase.CaseMetadata.SEED
        );

        EvalRun.ActualBehavior actual = OrchestrateOneShotHarnessHelper.runOnce(orch, repo, userRepo, mapper, tc);

        assertThat(actual.reply()).isEqualTo("建议挂神经内科");
        assertThat(actual.transferChain()).containsExactly("root_router_agent", "triage_agent");
        assertThat(actual.toolEvents()).hasSize(1);
        assertThat(actual.toolEvents().get(0).get("toolName")).isEqualTo("queryScheduleSlots");
        assertThat(actual.latencyMs()).isGreaterThanOrEqualTo(0);
        assertThat(actual.errorMessage()).isNull();
    }

    @Test
    void returnsErrorBehaviorWhenOrchestratorThrows() {
        AdkOrchestrator orch = mock(AdkOrchestrator.class);
        when(orch.orchestrateOneShot(anyLong(), anyLong(), anyString()))
                .thenThrow(new RuntimeException("LLM unavailable"));

        ConversationRepository repo = mock(ConversationRepository.class);
        when(repo.save(any())).thenAnswer(inv -> assignId(inv.getArgument(0), 42L));
        UserAccountRepository userRepo = mock(UserAccountRepository.class);
        when(userRepo.findById(anyLong())).thenReturn(Optional.of(new UserAccountEntity()));

        TestCase tc = new TestCase(
                "c1", TestCase.Category.ROUTING, "x",
                new TestCase.ContextPreset(1L, null, List.of()),
                TestCase.Expectations.EMPTY,
                TestCase.CaseMetadata.SEED
        );

        EvalRun.ActualBehavior actual = OrchestrateOneShotHarnessHelper.runOnce(orch, repo, userRepo, mapper, tc);

        assertThat(actual.errorMessage()).contains("orchestrate threw");
        assertThat(actual.errorMessage()).contains("LLM unavailable");
        assertThat(actual.reply()).isEmpty();
        assertThat(actual.hallucinationRisk()).isEqualTo("HIGH");
    }

    @Test
    void returnsErrorBehaviorWhenUserMissing() {
        AdkOrchestrator orch = mock(AdkOrchestrator.class);
        ConversationRepository repo = mock(ConversationRepository.class);
        UserAccountRepository userRepo = mock(UserAccountRepository.class);
        when(userRepo.findById(999L)).thenReturn(Optional.empty());

        TestCase tc = new TestCase(
                "c1", TestCase.Category.ROUTING, "x",
                new TestCase.ContextPreset(999L, null, List.of()),
                TestCase.Expectations.EMPTY,
                TestCase.CaseMetadata.SEED
        );

        EvalRun.ActualBehavior actual = OrchestrateOneShotHarnessHelper.runOnce(orch, repo, userRepo, mapper, tc);

        assertThat(actual.errorMessage()).contains("conversation creation failed");
        assertThat(actual.errorMessage()).contains("user_account id=999");
    }

    @Test
    void defaultsToUserIdOneWhenContextNull() {
        AdkOrchestrator orch = mock(AdkOrchestrator.class);
        when(orch.orchestrateOneShot(anyLong(), anyLong(), anyString()))
                .thenReturn(stubResult("ok", "{\"transferChain\":[],\"toolEvents\":[]}"));

        ConversationRepository repo = mock(ConversationRepository.class);
        when(repo.save(any())).thenAnswer(inv -> assignId(inv.getArgument(0), 42L));
        UserAccountRepository userRepo = mock(UserAccountRepository.class);
        when(userRepo.findById(1L)).thenReturn(Optional.of(new UserAccountEntity()));

        // context 为 null → 走默认 user id=1
        TestCase tc = new TestCase(
                "c1", TestCase.Category.ROUTING, "x",
                null,
                null,
                null
        );

        EvalRun.ActualBehavior actual = OrchestrateOneShotHarnessHelper.runOnce(orch, repo, userRepo, mapper, tc);
        assertThat(actual.reply()).isEqualTo("ok");
    }

    private static ConversationEntity assignId(ConversationEntity entity, long id) {
        try {
            java.lang.reflect.Field idField = ConversationEntity.class.getSuperclass().getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(entity, id);
        } catch (ReflectiveOperationException ex) {
            throw new RuntimeException(ex);
        }
        return entity;
    }

    private static AgentRunResult stubResult(String reply, String toolCallJson) {
        return new AgentRunResult(
                new IntentDecision("ADK_ROUTED", RouteMode.CHAT_ONLY, false, List.of()),
                reply,
                List.of(),
                List.of(),
                "[]",
                toolCallJson,
                "{}",
                "[]",
                "LOW",
                "[]"
        );
    }
}
