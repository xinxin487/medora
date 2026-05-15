package com.medora.agent.tool;

import com.medora.common.exception.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class ToolRegistry {

    private final Map<String, ToolDefinition> tools;

    public ToolRegistry(List<ToolDefinition> toolDefinitions) {
        this.tools = toolDefinitions.stream()
                .collect(Collectors.toUnmodifiableMap(ToolDefinition::name, Function.identity()));
    }

    public ToolDefinition get(String toolName) {
        ToolDefinition tool = tools.get(toolName);
        if (tool == null) {
            throw new BusinessException("AGENT_TOOL_NOT_FOUND", HttpStatus.UNPROCESSABLE_ENTITY,
                    "tool is not registered: " + toolName);
        }
        return tool;
    }
}
