package com.medora.agent.tool;

import java.util.Map;

public interface ToolDefinition {

    String name();

    String description();

    ToolExecutionResult execute(Map<String, Object> arguments, ToolExecutionContext context);
}
