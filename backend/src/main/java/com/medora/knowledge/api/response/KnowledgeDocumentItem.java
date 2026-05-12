package com.medora.knowledge.api.response;

import java.time.OffsetDateTime;

public record KnowledgeDocumentItem(
        String documentId,
        String title,
        String bizScope,
        String publishStatus,
        String ingestStatus,
        Long chunkCount,
        OffsetDateTime uploadedAt
) {
}
