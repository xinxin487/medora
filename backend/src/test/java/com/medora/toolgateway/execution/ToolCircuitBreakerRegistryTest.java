package com.medora.toolgateway.execution;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class ToolCircuitBreakerRegistryTest {

    @Test
    void opensAfterConsecutiveFailuresThenAllowsOneHalfOpenProbeAfterCooldown() {
        MutableClock clock = new MutableClock(Instant.parse("2026-09-21T14:00:00Z"));
        ToolCircuitBreakerRegistry registry = new ToolCircuitBreakerRegistry(2, Duration.ofSeconds(10), clock);

        registry.recordFailure("local.searchKnowledge@1");
        registry.recordFailure("local.searchKnowledge@1");

        assertThat(registry.state("local.searchKnowledge@1")).isEqualTo(ToolCircuitBreakerRegistry.State.OPEN);
        assertThat(registry.allow("local.searchKnowledge@1")).isFalse();
        clock.advance(Duration.ofSeconds(10));
        assertThat(registry.allow("local.searchKnowledge@1")).isTrue();
        assertThat(registry.allow("local.searchKnowledge@1")).isFalse();

        registry.recordSuccess("local.searchKnowledge@1");

        assertThat(registry.state("local.searchKnowledge@1")).isEqualTo(ToolCircuitBreakerRegistry.State.CLOSED);
        assertThat(registry.allow("local.searchKnowledge@1")).isTrue();
    }

    private static final class MutableClock extends Clock {
        private Instant instant;
        private MutableClock(Instant instant) { this.instant = instant; }
        void advance(Duration duration) { instant = instant.plus(duration); }
        @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return instant; }
    }
}
