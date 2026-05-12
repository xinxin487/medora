package com.medora.knowledge.api.response;

public record KnowledgeUploadResponse(
        String documentId,
        String title,
        String bizScope,
        String publishStatus,
        String ingestStatus,
        int chunkCount
) {
}
