package com.medora.agent.context;

import java.util.List;
import java.util.Map;

@FunctionalInterface
public interface ContextProvider {
    Result load(ContextBuildRequest request);
    record Result(String source, List<ContextItem> items, Map<String, Object> businessState) {
        public Result { items = items == null ? List.of() : List.copyOf(items); businessState = businessState == null ? Map.of() : Map.copyOf(businessState); }
    }
}
