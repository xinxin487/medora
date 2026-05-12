package com.medora.knowledge.service;

public record RetrievalResult(
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
