package com.medora.knowledge.service;

public record RetrievalResult(
        String documentId,
        Long chunkId,
        Integer chunkIndex,
        String title,
        String bizScope,
        Double score,
        String sectionPath,
        String snippet,
        String context
) {
    public RetrievalResult(String documentId, Long chunkId, Integer chunkIndex, String title,
                           String bizScope, Double score, String sectionPath, String snippet) {
        this(documentId, chunkId, chunkIndex, title, bizScope, score, sectionPath, snippet, snippet);
    }
}
