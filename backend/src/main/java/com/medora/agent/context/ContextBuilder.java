package com.medora.agent.context;

import com.medora.memory.governance.MemorySelector;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

@Component
public class ContextBuilder {
    private final ContextProfileResolver resolver;
    private final TokenBudgetAllocator allocator;
    private final List<ContextProvider> providers;
    private final MemorySelector selector = new MemorySelector();
    private final TokenEstimator estimator = new TokenEstimator();

    public ContextBuilder(ContextProfileResolver resolver, TokenBudgetAllocator allocator,
                          List<ContextProvider> providers) {
        this.resolver = resolver;
        this.allocator = allocator;
        this.providers = List.copyOf(providers);
    }

    public AgentContextBundle build(ContextBuildRequest request) {
        ContextProfile profile = resolver.resolve(request.message(), request.sceneType());
        ContextBudget budget = allocator.allocate(profile, request.maxTokens());
        List<ContextItem> items = new ArrayList<>();
        List<ContextDecision> decisions = new ArrayList<>();
        Map<String, Object> businessState = new LinkedHashMap<>();

        for (ContextProvider provider : providers) {
            try {
                ContextProvider.Result result = provider.load(request);
                items.addAll(result.items());
                businessState.putAll(result.businessState());
                decisions.addAll(result.decisions());
            } catch (Exception ignored) {
                decisions.add(new ContextDecision(provider.sourceName(), "", 0,
                        false, "PROVIDER_UNAVAILABLE"));
            }
        }

        Set<String> fingerprints = new HashSet<>();
        String summary = "";
        int summaryUsed = 0;
        for (ContextItem item : items) {
            if (!"SUMMARY".equals(item.source())) continue;
            if (summaryUsed + item.estimatedTokens() > budget.summary()) {
                decisions.add(decision(item, false, "BUDGET_EXCEEDED"));
            } else if (!fingerprints.add(item.fingerprint())) {
                decisions.add(decision(item, false, "DUPLICATE"));
            } else {
                summary = item.content();
                summaryUsed += item.estimatedTokens();
                decisions.add(decision(item, true, "SELECTED"));
            }
        }
        List<ContextItem> recentCandidates = items.stream()
                .filter(item -> "RECENT".equals(item.source())).toList();
        List<ContextItem> recentSelected = new ArrayList<>();
        int recentUsed = 0;
        int recentAllowance = Math.max(0, budget.currentTurns() - estimator.estimate(request.message()));
        for (int index = recentCandidates.size() - 1; index >= 0; index--) {
            ContextItem item = recentCandidates.get(index);
            if (recentUsed + item.estimatedTokens() > recentAllowance) {
                decisions.add(decision(item, false, "BUDGET_EXCEEDED"));
            } else if (!fingerprints.add(item.fingerprint())) {
                decisions.add(decision(item, false, "DUPLICATE"));
            } else {
                recentSelected.add(item);
                recentUsed += item.estimatedTokens();
                decisions.add(decision(item, true, "SELECTED"));
            }
        }
        java.util.Collections.reverse(recentSelected);
        List<String> recent = recentSelected.stream().map(ContextItem::content).toList();
        List<ContextItem> memoryCandidates = new ArrayList<>();
        for (ContextItem item : items) {
            if (!"MEMORY".equals(item.source())) continue;
            String factKey = ContextFingerprint.periodPreferenceKey(item.content());
            boolean sameStructuredFact = !factKey.isEmpty()
                    && (factKey.equals(ContextFingerprint.periodPreferenceKey(summary))
                    || recent.stream().anyMatch(turn -> factKey.equals(ContextFingerprint.periodPreferenceKey(turn))));
            if (summary.contains(item.content()) || recent.stream().anyMatch(turn -> turn.contains(item.content()))
                    || sameStructuredFact) {
                decisions.add(decision(item, false, "DUPLICATE"));
            } else {
                memoryCandidates.add(item);
            }
        }
        MemorySelector.Selection memories = selector.select(profile,
                memoryCandidates, budget.memory(), List.copyOf(fingerprints));
        memories.items().forEach(item -> fingerprints.add(item.fingerprint()));
        MemorySelector.Selection rag = selector.select(profile,
                items.stream().filter(item -> "RAG".equals(item.source())).toList(),
                budget.rag(), List.copyOf(fingerprints));
        decisions.addAll(memories.decisions());
        decisions.addAll(rag.decisions());

        Map<String, Object> boundedBusiness = new LinkedHashMap<>();
        int businessUsed = 0;
        for (Map.Entry<String, Object> entry : businessState.entrySet()) {
            String content = entry.getKey() + ":" + entry.getValue();
            int tokens = estimator.estimate(content);
            if (businessUsed + tokens > budget.business()) {
                decisions.add(new ContextDecision("BUSINESS", ContextFingerprint.of(content), tokens,
                        false, "BUDGET_EXCEEDED"));
            } else {
                boundedBusiness.put(entry.getKey(), entry.getValue());
                businessUsed += tokens;
                decisions.add(new ContextDecision("BUSINESS", ContextFingerprint.of(content), tokens,
                        true, "SELECTED"));
            }
        }

        return new AgentContextBundle(profile, budget, request.message(), summary, recent,
                memories.items().stream().map(ContextItem::content).toList(),
                rag.items().stream().map(ContextItem::content).toList(),
                boundedBusiness, decisions);
    }

    private ContextDecision decision(ContextItem item, boolean selected, String reason) {
        return new ContextDecision(item.source(), item.fingerprint(), item.estimatedTokens(), selected, reason);
    }
}
