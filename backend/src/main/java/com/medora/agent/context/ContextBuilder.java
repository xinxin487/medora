package com.medora.agent.context;

import com.medora.memory.governance.MemorySelector;
import java.util.*;

public class ContextBuilder {
    private final ContextProfileResolver resolver; private final TokenBudgetAllocator allocator; private final List<ContextProvider> providers; private final MemorySelector selector = new MemorySelector();
    public ContextBuilder(ContextProfileResolver resolver, TokenBudgetAllocator allocator, List<ContextProvider> providers) { this.resolver = resolver; this.allocator = allocator; this.providers = List.copyOf(providers); }
    public AgentContextBundle build(ContextBuildRequest request) {
        ContextProfile profile = resolver.resolve(request.message(), request.sceneType()); ContextBudget budget = allocator.allocate(profile, request.maxTokens());
        List<ContextItem> items = new ArrayList<>(); List<ContextDecision> decisions = new ArrayList<>(); Map<String,Object> state = new LinkedHashMap<>();
        for (ContextProvider provider : providers) try { ContextProvider.Result result = provider.load(request); items.addAll(result.items()); state.putAll(result.businessState()); }
        catch (Exception ignored) { decisions.add(new ContextDecision("PROVIDER", "", 0, false, "PROVIDER_UNAVAILABLE")); }
        MemorySelector.Selection selection = selector.select(profile, items, budget.memory() + budget.rag() + budget.business(), List.of()); decisions.addAll(selection.decisions());
        List<String> memories = selection.items().stream().filter(i -> "MEMORY".equals(i.source())).map(ContextItem::content).toList();
        List<String> rag = selection.items().stream().filter(i -> "RAG".equals(i.source())).map(ContextItem::content).toList();
        return new AgentContextBundle(profile, budget, request.message(), "", List.of(), memories, rag, state, decisions);
    }
}
