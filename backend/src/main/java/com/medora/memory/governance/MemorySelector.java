package com.medora.memory.governance;

import com.medora.agent.context.ContextDecision;
import com.medora.agent.context.ContextItem;
import com.medora.agent.context.ContextProfile;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MemorySelector {
    public record Selection(List<ContextItem> items, List<ContextDecision> decisions) { }

    public Selection select(ContextProfile profile, List<ContextItem> candidates, int budget,
                            List<String> knownFingerprints) {
        List<ContextItem> selected = new ArrayList<>();
        List<ContextDecision> decisions = new ArrayList<>();
        Set<String> seen = new HashSet<>(knownFingerprints == null ? List.of() : knownFingerprints);
        Set<String> singleValues = new HashSet<>();
        Map<String, Integer> historyCounts = new HashMap<>();
        int used = 0;
        OffsetDateTime now = OffsetDateTime.now();
        List<ContextItem> sorted = new ArrayList<>(candidates == null ? List.of() : candidates);
        sorted.sort(Comparator.comparingDouble((ContextItem item) -> score(profile, item, now)).reversed());
        for (ContextItem item : sorted) {
            String reason = null;
            if (item.expiresAt() != null && !item.expiresAt().isAfter(now)) reason = "EXPIRED";
            else if (!matches(profile, item)) reason = "PROFILE_MISMATCH";
            else if (seen.contains(item.fingerprint())) reason = "DUPLICATE";
            else if (MemoryCardinality.forType(item.type()) == MemoryCardinality.SINGLE_VALUE
                    && singleValues.contains(item.type())) reason = "CONFLICT_LOSER";
            else if (isHistory(item.type()) && historyCounts.getOrDefault(item.type(), 0) >= 2)
                reason = "TYPE_LIMIT";
            else if (used + item.estimatedTokens() > Math.max(0, budget)) reason = "BUDGET_EXCEEDED";
            if (reason != null) {
                decisions.add(decision(item, false, reason));
                continue;
            }
            selected.add(item);
            used += item.estimatedTokens();
            seen.add(item.fingerprint());
            if (MemoryCardinality.forType(item.type()) == MemoryCardinality.SINGLE_VALUE)
                singleValues.add(item.type());
            if (isHistory(item.type())) historyCounts.merge(item.type(), 1, Integer::sum);
            decisions.add(decision(item, true, "SELECTED"));
        }
        return new Selection(List.copyOf(selected), List.copyOf(decisions));
    }

    private boolean matches(ContextProfile profile, ContextItem item) {
        return profile == ContextProfile.BOOKING
                || !("LAST_SUCCESSFUL_APPOINTMENT".equals(item.type()) || isHistory(item.type()));
    }

    private boolean isHistory(String type) {
        return type != null && type.contains("HISTORY");
    }

    private double score(ContextProfile profile, ContextItem item, OffsetDateTime now) {
        return relevance(profile, item.type()) * .40
                + Math.max(0, Math.min(1, item.confidence())) * .25
                + sourceRank(item.sourceQuality()) * .20
                + freshness(item.occurredAt(), now) * .15;
    }

    private double relevance(ContextProfile profile, String type) {
        if (profile == ContextProfile.BOOKING) return isHistory(type) ? .8 : 1.0;
        if ("PREFERRED_DEPARTMENT".equals(type)) return profile == ContextProfile.TRIAGE ? .9 : .8;
        if ("PREFERRED_PERIOD".equals(type)) return profile == ContextProfile.KNOWLEDGE ? .5 : .6;
        return .4;
    }

    private double freshness(OffsetDateTime occurredAt, OffsetDateTime now) {
        if (occurredAt == null) return .5;
        long ageDays = Math.max(0, ChronoUnit.DAYS.between(occurredAt, now));
        return Math.max(0, 1.0 - ageDays / 180.0);
    }

    private double sourceRank(String source) {
        return switch (source == null ? "" : source) {
            case "USER_EXPLICIT" -> 1;
            case "BOOK_APPOINTMENT" -> .8;
            case "REPEATED_SIGNAL" -> .6;
            case "USER_MENTION" -> .4;
            case "LLM_EXTRACTED", "TOOL_QUERY" -> .2;
            default -> .4;
        };
    }

    private ContextDecision decision(ContextItem item, boolean selected, String reason) {
        return new ContextDecision(item.source(), item.fingerprint(), item.estimatedTokens(), selected, reason);
    }
}
