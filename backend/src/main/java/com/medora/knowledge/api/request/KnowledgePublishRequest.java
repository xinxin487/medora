package com.medora.knowledge.api.request;

import jakarta.validation.constraints.NotBlank;

public record KnowledgePublishRequest(
        @NotBlank String publishAction
) {
}
