package com.medora.agent.tool;

import com.medora.agent.application.IntentDecision;
import com.medora.common.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class ToolExecutor {

    private static final Logger log = LoggerFactory.getLogger(ToolExecutor.class);

    private final ToolRegistry toolRegistry;

    public ToolExecutor(ToolRegistry toolRegistry) {
        this.toolRegistry = toolRegistry;
    }

    public List<ToolExecutionResult> execute(List<IntentDecision.ToolInvocation> invocations,
                                             ToolExecutionContext context) {
        List<ToolExecutionResult> results = new ArrayList<>();
        for (IntentDecision.ToolInvocation invocation : invocations) {
            ToolDefinition tool = toolRegistry.get(invocation.toolName());
            try {
                results.add(tool.execute(invocation.arguments(), context));
            } catch (BusinessException ex) {
                results.add(ToolExecutionResult.failed(tool.name(), ex.getCode(), ex.getMessage()));
            } catch (RuntimeException ex) {
                log.warn("Agent tool execution failed. toolName={}, traceId={}", tool.name(), context.traceId(), ex);
                results.add(ToolExecutionResult.failed(tool.name(), "AGENT_TOOL_FAILED", "tool execution failed"));
            }
        }
        return results;
    }
}
