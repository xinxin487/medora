package com.medora.common.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.List;

@Component
public class MedoraMetricsService {

    private final MeterRegistry meterRegistry;

    public MedoraMetricsService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void recordAgentTrace(String intentType, String routeMode, String status, long latencyMs) {
        Tags tags = Tags.of(
                Tag.of("intent", normalize(intentType, "UNKNOWN")),
                Tag.of("route", normalize(routeMode, "UNKNOWN")),
                Tag.of("status", normalize(status, "UNKNOWN"))
        );
        meterRegistry.counter("medora.agent.trace.completed", tags).increment();
        meterRegistry.timer("medora.agent.trace.latency", tags)
                .record(Duration.ofMillis(Math.max(latencyMs, 0)));
    }

    public void recordAppointmentCommand(String actionType, String result) {
        meterRegistry.counter(
                "medora.appointment.command.completed",
                List.of(
                        Tag.of("action", normalize(actionType, "UNKNOWN")),
                        Tag.of("result", normalize(result, "UNKNOWN"))
                )
        ).increment();
    }

    public void recordAuditEvent(String operatorType, String actionType, String resourceType) {
        meterRegistry.counter(
                "medora.audit.event.created",
                List.of(
                        Tag.of("operator", normalize(operatorType, "UNKNOWN")),
                        Tag.of("action", normalize(actionType, "UNKNOWN")),
                        Tag.of("resource", normalize(resourceType, "UNKNOWN"))
                )
        ).increment();
    }

    private String normalize(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase() : fallback;
    }
}
