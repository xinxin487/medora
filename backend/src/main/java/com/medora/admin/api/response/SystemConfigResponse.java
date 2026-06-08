package com.medora.admin.api.response;

public record SystemConfigResponse(
        RagConfig rag,
        AiConfig ai
) {
    public record RagConfig(
            int defaultTopK,
            int maxTopK,
            double minScoreThreshold,
            double keywordWeight,
            double vectorWeight
    ) {
    }

    public record AiConfig(
            String provider,
            String chatModel,
            String embeddingModel,
            int embeddingDimensions,
            int contextCacheTtlSeconds
    ) {
    }
}
