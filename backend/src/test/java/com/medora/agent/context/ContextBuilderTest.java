package com.medora.agent.context;

import org.junit.jupiter.api.Test;
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
}
