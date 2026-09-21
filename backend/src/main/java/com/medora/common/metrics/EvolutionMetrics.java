package com.medora.common.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Set;

@Component
public class EvolutionMetrics {

    private static final Set<String> PROFILES = Set.of("GENERAL", "TRIAGE", "BOOKING", "KNOWLEDGE");
    private static final Set<String> STATUSES = Set.of("STARTED", "SUCCESS", "DEGRADED", "FAILED", "TIMEOUT", "UNAVAILABLE");
    private static final Set<String> RAG_STAGES = Set.of("CHUNK", "KEYWORD", "VECTOR", "FUSION", "RERANK", "ASSEMBLY");
    private static final Set<String> TOOL_FAMILIES = Set.of("LOCAL", "MCP");
    private static final Set<String> APPOINTMENT_OPERATIONS = Set.of("CREATE", "PREPARE", "CONFIRM", "CANCEL", "EXPIRE", "PROMOTE");

    private final MeterRegistry meterRegistry;

    public EvolutionMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void recordContextBuild(String profile, String status) {
        meterRegistry.counter("medora.context.build", Tags.of(
                "profile", normalize(profile, PROFILES),
                "status", normalize(status, STATUSES)
        )).increment();
    }

    public void recordRagStage(String stage, String status, Duration duration) {
        Tags tags = Tags.of(
                "stage", normalize(stage, RAG_STAGES),
                "status", normalize(status, STATUSES)
        );
        meterRegistry.counter("medora.rag.stage", tags).increment();
        meterRegistry.timer("medora.rag.stage.latency", tags)
                .record(duration == null || duration.isNegative() ? Duration.ZERO : duration);
    }

    public void recordToolCall(String toolFamily, String status, Duration duration) {
        Tags tags = Tags.of(
                "family", normalize(toolFamily, TOOL_FAMILIES),
                "status", normalize(status, STATUSES)
        );
        meterRegistry.counter("medora.tool.gateway.call", tags).increment();
        meterRegistry.timer("medora.tool.gateway.latency", tags)
                .record(duration == null || duration.isNegative() ? Duration.ZERO : duration);
    }

    public void recordAppointmentTransition(String operation, String status) {
        meterRegistry.counter("medora.appointment.transition", Tags.of(
                "operation", normalize(operation, APPOINTMENT_OPERATIONS),
                "status", normalize(status, STATUSES)
        )).increment();
    }

    private String normalize(String value, Set<String> allowed) {
        if (value == null) {
            return "UNKNOWN";
        }
        String normalized = value.trim().toUpperCase();
        return allowed.contains(normalized) ? normalized : "UNKNOWN";
    }
}
