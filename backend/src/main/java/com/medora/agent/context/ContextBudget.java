package com.medora.agent.context;

public record ContextBudget(int system, int currentTurns, int summary, int rag, int memory, int business) {
    public int totalAllocated() { return system + currentTurns + summary + rag + memory + business; }
}
