package com.medora.knowledge.api.response;

import java.time.OffsetDateTime;
import java.util.List;

public record KnowledgeDocumentResponse(
        String documentId,
        String title,
        String sourceType,
        String sourceUri,
        String bizScope,
        String publishStatus,
        String ingestStatus,
        List<String> departmentTags,
        List<String> doctorTags,
        Integer versionNo,
        Long chunkCount,
        OffsetDateTime uploadedAt
) {
}
