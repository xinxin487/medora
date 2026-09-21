package com.medora.agent.context;

import java.util.List;
import java.util.Map;

public record AgentContextBundle(ContextProfile profile, ContextBudget budget, String currentMessage,
                                 String summary, List<String> recentTurns, List<String> memories,
                                 List<String> ragEvidence, Map<String, Object> businessState,
                                 List<String> decisions) {
    public AgentContextBundle {
        recentTurns = recentTurns == null ? List.of() : List.copyOf(recentTurns);
        memories = memories == null ? List.of() : List.copyOf(memories);
        ragEvidence = ragEvidence == null ? List.of() : List.copyOf(ragEvidence);
        businessState = businessState == null ? Map.of() : Map.copyOf(businessState);
        decisions = decisions == null ? List.of() : List.copyOf(decisions);
    }
}
