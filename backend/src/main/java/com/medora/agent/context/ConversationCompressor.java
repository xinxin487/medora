package com.medora.agent.context;

import java.util.List;

public class ConversationCompressor {
    public record Compression(String goals, List<String> confirmedFacts, List<String> openTasks, List<String> recentToolResults, List<String> recentTurns) { }
    public Compression compress(List<String> turns, int keepNewest) {
        List<String> safe = turns == null ? List.of() : turns; int start = Math.max(0, safe.size() - Math.max(0, keepNewest));
        return new Compression("", List.of(), List.of(), List.of(), List.copyOf(safe.subList(start, safe.size())));
    }
}
