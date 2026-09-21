package com.medora.memory.governance;

public class MemoryConflictResolver {
    public enum Decision { KEEP_EXISTING, REPLACE_EXISTING, COEXIST }
    public Decision resolve(MemoryCandidate existing, MemoryCandidate incoming) {
        if (MemoryCardinality.forType(incoming.memoryType()) == MemoryCardinality.MULTI_VALUE) return Decision.COEXIST;
        int sourceComparison = Integer.compare(rank(incoming.source()), rank(existing.source()));
        if (sourceComparison < 0) return Decision.KEEP_EXISTING;
        if (sourceComparison > 0) return Decision.REPLACE_EXISTING;
        return incoming.occurredAt().isAfter(existing.occurredAt()) ? Decision.REPLACE_EXISTING : Decision.KEEP_EXISTING;
    }
    private int rank(String source) { return switch (source == null ? "" : source) { case "USER_EXPLICIT" -> 5; case "BOOK_APPOINTMENT" -> 4; case "REPEATED_SIGNAL" -> 3; case "USER_MENTION" -> 2; case "LLM_EXTRACTED", "TOOL_QUERY" -> 1; default -> 0; }; }
}
