package com.medora.knowledge.api.request;

import jakarta.validation.constraints.NotBlank;

public record KnowledgeSearchTestRequest(
        @NotBlank String query,
        String bizScope,
        Integer topK
) {
}
