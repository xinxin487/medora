package com.medora.memory.governance;

import com.medora.agent.context.ContextDecision;
import com.medora.agent.context.ContextItem;
import com.medora.agent.context.ContextProfile;
import java.time.OffsetDateTime;
import java.util.*;

public class MemorySelector {
    public record Selection(List<ContextItem> items, List<ContextDecision> decisions) { }
    public Selection select(ContextProfile profile, List<ContextItem> candidates, int budget, List<String> knownFingerprints) {
        List<ContextItem> selected = new ArrayList<>(); List<ContextDecision> decisions = new ArrayList<>();
        Set<String> seen = new HashSet<>(knownFingerprints == null ? List.of() : knownFingerprints);
        Map<String, ContextItem> singleValues = new HashMap<>(); int used = 0;
        List<ContextItem> sorted = new ArrayList<>(candidates == null ? List.of() : candidates);
        sorted.sort(Comparator.comparingDouble(this::score).reversed());
        for (ContextItem item : sorted) {
            if (item.expiresAt() != null && !item.expiresAt().isAfter(OffsetDateTime.now())) { decisions.add(decision(item, false, "EXPIRED")); continue; }
            if (!matches(profile, item)) { decisions.add(decision(item, false, "PROFILE_MISMATCH")); continue; }
            if (!seen.add(item.fingerprint())) { decisions.add(decision(item, false, "DUPLICATE")); continue; }
            if (MemoryCardinality.forType(item.type()) == MemoryCardinality.SINGLE_VALUE && singleValues.containsKey(item.type())) { decisions.add(decision(item, false, "CONFLICT_LOSER")); continue; }
            if (used + item.estimatedTokens() > Math.max(0, budget)) { decisions.add(decision(item, false, "BUDGET_EXCEEDED")); continue; }
            selected.add(item); used += item.estimatedTokens(); singleValues.put(item.type(), item); decisions.add(decision(item, true, "SELECTED"));
        }
        return new Selection(List.copyOf(selected), List.copyOf(decisions));
    }
    private boolean matches(ContextProfile profile, ContextItem item) {
        return profile != ContextProfile.KNOWLEDGE || !"LAST_SUCCESSFUL_APPOINTMENT".equals(item.type());
    }
    private double score(ContextItem item) { return item.confidence() * .25 + sourceRank(item.sourceQuality()) * .20 + .55; }
    private double sourceRank(String source) { return switch (source == null ? "" : source) { case "USER_EXPLICIT" -> 1; case "BOOK_APPOINTMENT" -> .8; case "TOOL_QUERY" -> .2; default -> .4; }; }
    private ContextDecision decision(ContextItem item, boolean selected, String reason) { return new ContextDecision(item.source(), item.fingerprint(), item.estimatedTokens(), selected, reason); }
}
