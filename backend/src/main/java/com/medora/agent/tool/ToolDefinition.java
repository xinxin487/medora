package com.medora.agent.tool;

import java.util.Map;

public interface ToolDefinition {

    String name();

    String description();

    default ToolRiskLevel riskLevel() {
        return ToolRiskLevel.LOW;
    }

    ToolExecutionResult execute(Map<String, Object> arguments, ToolExecutionContext context);
}
