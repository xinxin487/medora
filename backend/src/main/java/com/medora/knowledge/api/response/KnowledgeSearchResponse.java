package com.medora.knowledge.api.response;

import java.util.List;

public record KnowledgeSearchResponse(
        String query,
        List<KnowledgeSearchItem> items
) {
}
