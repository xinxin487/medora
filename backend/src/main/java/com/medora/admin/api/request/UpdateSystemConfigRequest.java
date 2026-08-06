package com.medora.admin.api.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record UpdateSystemConfigRequest(
        RagConfig rag
) {
    public record RagConfig(
            @NotNull @Min(1) @Max(50) Integer defaultTopK,
            @NotNull @Min(1) @Max(100) Integer maxTopK,
            @NotNull @Min(0) @Max(100) Double minScoreThreshold,
            @NotNull @Min(0) @Max(100) Double keywordWeight,
            @NotNull @Min(0) @Max(100) Double vectorWeight
    ) {
    }
}
