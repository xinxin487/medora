package com.medora.agent.context;

import com.medora.knowledge.service.RetrievalResult;
import com.medora.knowledge.service.RetrievalService;
import com.medora.memory.service.ConversationSummaryService;
import com.medora.memory.service.MemoryService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;

class ContextProviderWiringTest {
    @Test
    void springBuilderLoadsRealConversationMemoryAndKnowledgeProviders() {
        ConversationSummaryService summaries = mock(ConversationSummaryService.class);
        MemoryService memories = mock(MemoryService.class);
        RetrievalService retrieval = mock(RetrievalService.class);
        when(summaries.loadContext(2L)).thenReturn(new ConversationSummaryService.ConversationContextSnapshot(
                "GENERAL", "用户已确认呼吸内科", Map.of(), Map.of("department", "RESPIRATORY"),
                List.of(new ConversationSummaryService.RecentMessage("USER", "咳嗽三天"))));
        when(memories.loadContextCandidates(1L)).thenReturn(List.of(
                new MemoryService.MemoryContextCandidate("PREFERRED_PERIOD", "偏好上午就诊",
                        0.8, "USER_EXPLICIT", "ACTIVE", null)));
        when(retrieval.retrieve("有哪些注意事项", null, 3)).thenReturn(List.of(
                new RetrievalResult("doc-1", 11L, 0, "就医指南", "GENERAL", 0.9,
                        "就诊准备", "记录症状持续时间", "记录症状持续时间")));

        new ApplicationContextRunner()
                .withUserConfiguration(ContextConfiguration.class)
                .withBean(ConversationSummaryService.class, () -> summaries)
                .withBean(MemoryService.class, () -> memories)
                .withBean(RetrievalService.class, () -> retrieval)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    AgentContextBundle bundle = context.getBean(ContextBuilder.class).build(
                            new ContextBuildRequest(1L, 2L, "有哪些注意事项", "GENERAL", 1_000));
                    assertThat(bundle.summary()).contains("用户已确认呼吸内科");
                    assertThat(bundle.recentTurns()).anyMatch(turn -> turn.contains("咳嗽三天"));
                    assertThat(bundle.memories()).contains("偏好上午就诊");
                    assertThat(bundle.ragEvidence()).anyMatch(item -> item.contains("记录症状持续时间"));
                    assertThat(bundle.businessState()).containsEntry("department", "RESPIRATORY");
                });
    }

    @Test
    void ordinaryConversationDoesNotEagerlyCallKnowledgeRetrieval() {
        ConversationSummaryService summaries = mock(ConversationSummaryService.class);
        MemoryService memories = mock(MemoryService.class);
        RetrievalService retrieval = mock(RetrievalService.class);
        when(summaries.loadContext(2L)).thenReturn(new ConversationSummaryService.ConversationContextSnapshot(
                "GENERAL", "", Map.of(), Map.of(), List.of(
                new ConversationSummaryService.RecentMessage("USER", "你好"))));
        new ApplicationContextRunner().withUserConfiguration(ContextConfiguration.class)
                .withBean(ConversationSummaryService.class, () -> summaries)
                .withBean(MemoryService.class, () -> memories)
                .withBean(RetrievalService.class, () -> retrieval)
                .run(context -> {
                    AgentContextBundle bundle = context.getBean(ContextBuilder.class).build(
                            new ContextBuildRequest(1L, 2L, "你好", "GENERAL", 1_000));
                    assertThat(bundle.recentTurns()).isEmpty();
                    verifyNoInteractions(retrieval);
                });
    }

    @Test
    void realMemoryProviderExplainsExpiredInactiveAndProfileRejectedCandidates() {
        ConversationSummaryService summaries = mock(ConversationSummaryService.class);
        MemoryService memories = mock(MemoryService.class);
        RetrievalService retrieval = mock(RetrievalService.class);
        when(summaries.loadContext(2L)).thenReturn(new ConversationSummaryService.ConversationContextSnapshot(
                "GENERAL", "", Map.of(), Map.of(), List.of()));
        when(memories.loadContextCandidates(1L)).thenReturn(List.of(
                new MemoryService.MemoryContextCandidate("PREFERRED_PERIOD", "已过期的上午偏好", 0.9,
                        "USER_EXPLICIT", "ACTIVE", OffsetDateTime.now().minusDays(1)),
                new MemoryService.MemoryContextCandidate("PREFERRED_PERIOD", "未确认的下午偏好", 0.4,
                        "USER_MENTION", "CANDIDATE", null),
                new MemoryService.MemoryContextCandidate("LAST_SUCCESSFUL_APPOINTMENT", "以前的预约", 1.0,
                        "BOOK_APPOINTMENT", "ACTIVE", null)));

        new ApplicationContextRunner().withUserConfiguration(ContextConfiguration.class)
                .withBean(ConversationSummaryService.class, () -> summaries)
                .withBean(MemoryService.class, () -> memories)
                .withBean(RetrievalService.class, () -> retrieval)
                .run(context -> {
                    AgentContextBundle bundle = context.getBean(ContextBuilder.class).build(
                            new ContextBuildRequest(1L, 2L, "你好", "GENERAL", 1_000));
                    assertThat(bundle.memories()).isEmpty();
                    assertThat(bundle.decisions()).extracting(ContextDecision::reasonCode)
                            .contains("EXPIRED", "INACTIVE", "PROFILE_MISMATCH");
                    assertThat(bundle.decisions()).allSatisfy(decision ->
                            assertThat(decision.fingerprint()).doesNotContain("偏好", "预约"));
                });
    }

    @Configuration(proxyBeanMethods = false)
    @ComponentScan(basePackageClasses = ContextBuilder.class)
    static class ContextConfiguration { }
}
