package com.medora.admin.api.response;

import java.util.List;
import java.util.Map;

public record AdminToolDiagnosticItem(
        String qualifiedName, String description, String sourceType, String riskLevel,
        List<String> allowedAgents, boolean idempotent, boolean requiresConfirmation,
        String circuitState, long callCount, double successRate, long p95DurationMs,
        Map<String, Object> inputSchema, Map<String, Object> outputSchema
) {
}
