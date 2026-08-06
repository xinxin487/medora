package com.medora.knowledge.api.response;

public record KnowledgeSearchItem(
        String documentId,
        Long chunkId,
        Integer chunkIndex,
        String title,
        String bizScope,
        Double score,
        String sectionPath,
        String snippet
) {
}
