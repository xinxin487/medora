package com.medora.agent.application;

import com.medora.agent.tool.ToolExecutionResult;
import com.medora.knowledge.service.RetrievalResult;

import java.util.List;

public record AgentRunResult(
        IntentDecision intentDecision,
        String reply,
        List<RetrievalResult> retrievalResults,
        List<ToolExecutionResult> toolResults,
        String citationJson,
        String toolCallJson,
        String tokenUsageJson
) {
}
