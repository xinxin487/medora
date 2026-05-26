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
        String tokenUsageJson,
        String safetyEventsJson,
        String hallucinationRisk,
        String riskFactorsJson
) {
    public AgentRunResult {
        safetyEventsJson = safetyEventsJson == null || safetyEventsJson.isBlank() ? "[]" : safetyEventsJson;
        hallucinationRisk = hallucinationRisk == null || hallucinationRisk.isBlank() ? "LOW" : hallucinationRisk;
        riskFactorsJson = riskFactorsJson == null || riskFactorsJson.isBlank() ? "[]" : riskFactorsJson;
    }
}
