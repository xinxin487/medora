package com.medora.knowledge.api.query;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record KnowledgeDocumentListQuery(
        @Size(max = 100) String title,
        @Size(max = 30) String bizScope,
        @Size(max = 20) String publishStatus,
        @Size(max = 20) String ingestStatus,
        @Min(1) Integer pageNo,
        @Min(1) @Max(100) Integer pageSize
) {
}
