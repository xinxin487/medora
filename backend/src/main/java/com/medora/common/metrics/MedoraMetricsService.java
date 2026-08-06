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

    private static final String TAG_ROUTE = "route";
    private static final String TAG_STATUS = "status";
    private static final String TAG_OUTCOME = "outcome";
    private static final String TAG_LEVEL = "level";

    private final MeterRegistry meterRegistry;

    public MedoraMetricsService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void recordAgentTrace(String intentType, String routeMode, String status, long latencyMs) {
        Tags tags = Tags.of(
                Tag.of("intent", normalize(intentType, "UNKNOWN")),
                Tag.of(TAG_ROUTE, normalize(routeMode, "UNKNOWN")),
                Tag.of(TAG_STATUS, normalize(status, "UNKNOWN"))
        );
        meterRegistry.counter("medora.agent.trace.completed", tags).increment();
        meterRegistry.timer("medora.agent.trace.latency", tags)
                .record(Duration.ofMillis(Math.max(latencyMs, 0)));
    }

    /**
     * Phase 9.8 — 工具调用成功率(按工具名+结果维度)。
     * 由 AdkEventToSseAdapter 在 tool_status 事件触发时上报。
     */
    public void recordToolCall(String toolName, String status) {
        meterRegistry.counter(
                "medora.agent.tool.call",
                List.of(
                        Tag.of("tool", normalize(toolName, "UNKNOWN")),
                        Tag.of(TAG_STATUS, normalize(status, "UNKNOWN"))
                )
        ).increment();
    }

    /**
     * Phase 9.8 — 安全审查结果(ALLOW/BLOCK/APPEND_DISCLAIMER)。
     */
    public void recordSafetyOutcome(String routeMode, String outcome) {
        meterRegistry.counter(
                "medora.agent.safety.outcome",
                List.of(
                        Tag.of(TAG_ROUTE, normalize(routeMode, "ADK")),
                        Tag.of(TAG_OUTCOME, normalize(outcome, "UNKNOWN"))
                )
        ).increment();
    }

    /**
     * Phase 9.8 — 幻觉风险分布(LOW/MEDIUM/HIGH)。
     */
    public void recordHallucinationRisk(String routeMode, String level) {
        meterRegistry.counter(
                "medora.agent.hallucination",
                List.of(
                        Tag.of(TAG_ROUTE, normalize(routeMode, "ADK")),
                        Tag.of(TAG_LEVEL, normalize(level, "UNKNOWN"))
                )
        ).increment();
    }

    /**
     * Phase 9.8 — 引用覆盖率(检索结果数 vs 实际引用数)。
     * 用 distribution summary 记录覆盖率比值,counter 记录命中次数。
     */
    public void recordRetrievalCitation(String routeMode, int retrievalCount, int citedCount) {
        Tags tags = Tags.of(Tag.of(TAG_ROUTE, normalize(routeMode, "ADK")));
        meterRegistry.counter("medora.agent.retrieval.calls", tags).increment();
        meterRegistry.summary("medora.agent.retrieval.count", tags).record(retrievalCount);
        meterRegistry.summary("medora.agent.retrieval.cited", tags).record(citedCount);
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
