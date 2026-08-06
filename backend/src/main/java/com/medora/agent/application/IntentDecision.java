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
            Map<String, Object> arguments,
            ParamSource paramSource
    ) {
        public ToolInvocation(String toolName, Map<String, Object> arguments) {
            this(toolName, arguments, ParamSource.MODEL_INFERRED);
        }

        public static ToolInvocation userProvided(String toolName, Map<String, Object> arguments) {
            return new ToolInvocation(toolName, arguments, ParamSource.USER_PROVIDED);
        }
    }

    public enum ParamSource {
        USER_PROVIDED,
        MODEL_INFERRED
    }
}
