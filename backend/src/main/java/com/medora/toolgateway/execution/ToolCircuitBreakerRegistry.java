package com.medora.toolgateway.execution;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ToolCircuitBreakerRegistry {

    public enum State { CLOSED, OPEN, HALF_OPEN }

    private final int failureThreshold;
    private final Duration cooldown;
    private final Clock clock;
    private final ConcurrentHashMap<String, Entry> entries = new ConcurrentHashMap<>();

    public ToolCircuitBreakerRegistry() { this(3, Duration.ofSeconds(30), Clock.systemUTC()); }
    ToolCircuitBreakerRegistry(int failureThreshold, Duration cooldown, Clock clock) {
        this.failureThreshold = Math.max(1, failureThreshold);
        this.cooldown = cooldown.isNegative() ? Duration.ZERO : cooldown;
        this.clock = clock;
    }

    public boolean allow(String qualifiedToolName) {
        return entry(qualifiedToolName).allow(clock.instant());
    }

    public void recordSuccess(String qualifiedToolName) {
        entry(qualifiedToolName).recordSuccess();
    }

    public void recordFailure(String qualifiedToolName) {
        entry(qualifiedToolName).recordFailure(failureThreshold, cooldown, clock.instant());
    }

    public State state(String qualifiedToolName) {
        return entry(qualifiedToolName).state(clock.instant());
    }

    private Entry entry(String qualifiedToolName) {
        return entries.computeIfAbsent(qualifiedToolName, ignored -> new Entry());
    }

    private static final class Entry {
        private int failures;
        private State state = State.CLOSED;
        private Instant openUntil = Instant.MIN;

        synchronized boolean allow(Instant now) {
            if (state == State.OPEN && !now.isBefore(openUntil)) {
                state = State.HALF_OPEN;
                return true;
            }
            return state == State.CLOSED;
        }

        synchronized void recordSuccess() {
            failures = 0;
            state = State.CLOSED;
        }

        synchronized void recordFailure(int failureThreshold, Duration cooldown, Instant now) {
            failures++;
            if (state == State.HALF_OPEN || failures >= failureThreshold) {
                state = State.OPEN;
                openUntil = now.plus(cooldown);
            }
        }

        synchronized State state(Instant now) {
            return state;
        }
    }
}
