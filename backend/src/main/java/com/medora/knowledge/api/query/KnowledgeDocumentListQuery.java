package com.medora.knowledge.api.query;

public record KnowledgeDocumentListQuery(
        String title,
        String bizScope,
        String publishStatus,
        String ingestStatus,
        Integer pageNo,
        Integer pageSize
) {
}
