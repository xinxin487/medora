package com.medora.agent.tool;

public record ToolExecutionContext(
        Long userAccountId,
        String userMessage,
        String traceId,
        String conversationId
) {
}
