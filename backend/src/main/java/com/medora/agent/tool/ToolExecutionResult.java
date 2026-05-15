package com.medora.agent.tool;

public record ToolExecutionResult(
        String toolName,
        String status,
        Object data,
        String summary,
        String errorCode,
        String errorMessage
) {
    public static ToolExecutionResult success(String toolName, Object data, String summary) {
        return new ToolExecutionResult(toolName, "SUCCESS", data, summary, null, null);
    }

    public static ToolExecutionResult failed(String toolName, String errorCode, String errorMessage) {
        return new ToolExecutionResult(toolName, "FAILED", null, null, errorCode, errorMessage);
    }

    public boolean success() {
        return "SUCCESS".equals(status);
    }
}
