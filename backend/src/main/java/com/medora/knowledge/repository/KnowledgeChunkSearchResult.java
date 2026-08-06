package com.medora.knowledge.repository;

import java.util.UUID;

public record KnowledgeChunkSearchResult(
        Long chunkId,
        Integer chunkIndex,
        String sectionPath,
        String content,
        Double score,
        UUID documentPublicId,
        String title,
        String bizScope
) implements KnowledgeChunkSearchRow {

    @Override
    public Long getChunkId() {
        return chunkId;
    }

    @Override
    public Integer getChunkIndex() {
        return chunkIndex;
    }

    @Override
    public String getSectionPath() {
        return sectionPath;
    }

    @Override
    public String getContent() {
        return content;
    }

    @Override
    public Double getScore() {
        return score;
    }

    @Override
    public UUID getDocumentPublicId() {
        return documentPublicId;
    }

    @Override
    public String getTitle() {
        return title;
    }

    @Override
    public String getBizScope() {
        return bizScope;
    }
}
