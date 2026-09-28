package com.medora.agent.context;

import org.junit.jupiter.api.Test;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

class ContextBuilderTest {
    @Test void providerFailureDoesNotFailContextBuild() {
        ContextProvider failingSummary = request -> { throw new IllegalStateException("down"); };
        ContextBuilder builder = new ContextBuilder(new ContextProfileResolver(), new TokenBudgetAllocator(), List.of(failingSummary));
        ContextBuildRequest request = new ContextBuildRequest(1L, 2L, "帮我约明天下午呼吸内科", "GENERAL", 1_000);

        AgentContextBundle bundle = builder.build(request);

        assertThat(bundle.currentMessage()).isEqualTo(request.message());
        assertThat(bundle.decisions()).extracting(ContextDecision::reasonCode).contains("PROVIDER_UNAVAILABLE");
    }

    @Test void bookingProviderStateIsIncluded() {
        ContextProvider provider = request -> new ContextProvider.Result("BUSINESS", List.of(), Map.of("slotId", "s-1"));
        ContextBuilder builder = new ContextBuilder(new ContextProfileResolver(), new TokenBudgetAllocator(), List.of(provider));

        assertThat(builder.build(new ContextBuildRequest(1L, 2L, "我要预约", "GENERAL", 1_000)).businessState())
                .containsEntry("slotId", "s-1");
    }

    @Test void assemblesAllProvidedContextSourcesForTheAgent() {
        ContextProvider provider = request -> new ContextProvider.Result("CONVERSATION", List.of(
                item("SUMMARY", "用户已确认呼吸内科", "summary"),
                item("RECENT", "用户：咳嗽三天", "recent"),
                item("MEMORY", "偏好上午就诊", "memory"),
                item("RAG", "呼吸内科处理呼吸系统问题", "rag")
        ), Map.of("department", "RESPIRATORY"));
        ContextBuilder builder = new ContextBuilder(new ContextProfileResolver(), new TokenBudgetAllocator(), List.of(provider));

        AgentContextBundle bundle = builder.build(new ContextBuildRequest(1L, 2L, "有哪些注意事项", "GENERAL", 1_000));

        assertThat(bundle.summary()).isEqualTo("用户已确认呼吸内科");
        assertThat(bundle.recentTurns()).containsExactly("用户：咳嗽三天");
        assertThat(bundle.memories()).containsExactly("偏好上午就诊");
        assertThat(bundle.ragEvidence()).containsExactly("呼吸内科处理呼吸系统问题");
        assertThat(bundle.businessState()).containsEntry("department", "RESPIRATORY");
    }

    @Test void doesNotRepeatSummaryFactsOrExceedSectionBudgets() {
        ContextProvider provider = request -> new ContextProvider.Result("MIXED", List.of(
                item("SUMMARY", "已确认：偏好上午就诊", "summary", 8),
                item("RECENT", "用户：第一轮", "first", 15),
                item("RECENT", "用户：第二轮", "second", 15),
                item("MEMORY", "偏好上午就诊", "memory", 8),
                item("RAG", "足够长的检索段落", "rag", 50)
        ), Map.of());
        ContextBuilder builder = new ContextBuilder(new ContextProfileResolver(), new TokenBudgetAllocator(), List.of(provider));

        AgentContextBundle bundle = builder.build(new ContextBuildRequest(1L, 2L, "注意事项", "GENERAL", 80));

        assertThat(bundle.memories()).isEmpty();
        assertThat(bundle.recentTurns()).containsExactly("用户：第二轮");
        assertThat(bundle.ragEvidence()).isEmpty();
        assertThat(bundle.decisions()).extracting(ContextDecision::reasonCode)
                .contains("DUPLICATE", "BUDGET_EXCEEDED");
    }

    @Test void boundsBusinessStateAndReservesCurrentMessageBudget() {
        ContextProvider provider = request -> new ContextProvider.Result("MIXED", List.of(
                item("RECENT", "用户：上一轮较长消息", "recent", 20)),
                Map.of("note", "很长的业务状态".repeat(100)));
        ContextBuilder builder = new ContextBuilder(new ContextProfileResolver(), new TokenBudgetAllocator(), List.of(provider));

        AgentContextBundle bundle = builder.build(new ContextBuildRequest(1L, 2L,
                "当前问题内容需要占用预算".repeat(20), "GENERAL", 100));

        assertThat(bundle.recentTurns()).isEmpty();
        assertThat(bundle.businessState()).isEmpty();
        assertThat(bundle.decisions()).extracting(ContextDecision::reasonCode).contains("BUDGET_EXCEEDED");
    }

    @Test void deduplicatesEquivalentPeriodPreferenceAcrossDifferentWording() {
        ContextProvider provider = request -> new ContextProvider.Result("MIXED", List.of(
                item("SUMMARY", "已确认：用户希望早上挂号", "summary", 12),
                new ContextItem("MEMORY", "PREFERRED_PERIOD", "memory", "偏好上午就诊",
                        0.9, "USER_EXPLICIT", null, 8)), Map.of());
        ContextBuilder builder = new ContextBuilder(new ContextProfileResolver(), new TokenBudgetAllocator(),
                List.of(provider));

        AgentContextBundle bundle = builder.build(new ContextBuildRequest(1L, 2L, "我想预约", "GENERAL", 1_000));

        assertThat(bundle.memories()).isEmpty();
        assertThat(bundle.decisions()).anyMatch(decision -> "MEMORY".equals(decision.source())
                && "DUPLICATE".equals(decision.reasonCode()));
    }

    private ContextItem item(String source, String content, String fingerprint) {
        return new ContextItem(source, source, fingerprint, content, 0.9, "USER_EXPLICIT", (OffsetDateTime) null, 20);
    }

    private ContextItem item(String source, String content, String fingerprint, int tokens) {
        return new ContextItem(source, source, fingerprint, content, 0.9, "USER_EXPLICIT", null, tokens);
    }
}
