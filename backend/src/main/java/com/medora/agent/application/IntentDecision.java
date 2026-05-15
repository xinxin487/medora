package com.medora.agent.application;

import java.util.List;
import java.util.Map;

public record IntentDecision(
        String intentType,
        RouteMode routeMode,
        boolean needRetrieval,
        List<ToolInvocation> toolInvocations
) {
    public boolean needTool() {
        return toolInvocations != null && !toolInvocations.isEmpty();
    }

    public record ToolInvocation(
            String toolName,
            Map<String, Object> arguments
    ) {
    }
}
