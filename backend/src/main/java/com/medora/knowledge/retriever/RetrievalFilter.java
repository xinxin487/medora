package com.medora.knowledge.retriever;

import java.time.OffsetDateTime;
import java.util.List;

public record RetrievalFilter(String documentType, String departmentCode, String hospitalCode, List<String> tags, List<String> accessScopes, OffsetDateTime at) {
    public RetrievalFilter { tags = tags == null ? List.of() : List.copyOf(tags); accessScopes = accessScopes == null ? List.of() : List.copyOf(accessScopes); }
}
