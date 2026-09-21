package com.medora.common.metrics;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class EvolutionMetricsTest {

    @Test
    void recordsOnlyBoundedTagsForKnownValues() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        EvolutionMetrics metrics = new EvolutionMetrics(registry);

        metrics.recordContextBuild("BOOKING", "SUCCESS");
        metrics.recordRagStage("RERANK", "SUCCESS", Duration.ofMillis(15));
        metrics.recordToolCall("MCP", "SUCCESS", Duration.ofMillis(20));
        metrics.recordAppointmentTransition("CONFIRM", "SUCCESS");

        assertThat(registry.find("medora.context.build").tag("profile", "BOOKING").counter()).isNotNull();
        assertThat(registry.find("medora.rag.stage.latency").tag("stage", "RERANK").timer()).isNotNull();
        assertThat(registry.getMeters()).allSatisfy(meter ->
                assertThat(meter.getId().getTags())
                        .noneMatch(tag -> tag.getKey().equals("userId") || tag.getKey().equals("traceId")));
    }

    @Test
    void normalizesUnknownMetricDimensionsToUnknown() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        EvolutionMetrics metrics = new EvolutionMetrics(registry);

        metrics.recordContextBuild("patient-829", "free-form-status");

        assertThat(registry.find("medora.context.build")
                .tags("profile", "UNKNOWN", "status", "UNKNOWN")
                .counter())
                .isNotNull();
    }
}
