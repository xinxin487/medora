package com.medora.agent.context;

import org.springframework.stereotype.Component;

@Component
public class TokenBudgetAllocator {
    public ContextBudget allocate(ContextProfile profile, int maxTokens) {
        int total = Math.max(0, maxTokens);
        int system = portion(total, 20), current = portion(total, 25), summary = portion(total, 10);
        int rag = portion(total, profile == ContextProfile.BOOKING ? 15 : 30);
        int memory = portion(total, 10);
        int business = total - system - current - summary - rag - memory;
        return new ContextBudget(system, current, summary, rag, memory, business);
    }
    private int portion(int total, int percent) { return total * percent / 100; }
}
