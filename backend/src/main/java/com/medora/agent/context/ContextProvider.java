package com.medora.agent.context;

import java.util.List;
import java.util.Map;

@FunctionalInterface
public interface ContextProvider {
    Result load(ContextBuildRequest request);
    default String sourceName() { return getClass().getSimpleName(); }
    record Result(String source, List<ContextItem> items, Map<String, Object> businessState,
                  List<ContextDecision> decisions) {
        public Result(String source, List<ContextItem> items, Map<String, Object> businessState) {
            this(source, items, businessState, List.of());
        }
        public Result {
            items = items == null ? List.of() : List.copyOf(items);
            businessState = businessState == null ? Map.of() : Map.copyOf(businessState);
            decisions = decisions == null ? List.of() : List.copyOf(decisions);
        }
    }
}
