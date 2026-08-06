package com.medora.knowledge.repository;

import java.util.UUID;

public interface KnowledgeChunkSearchRow {

    Long getChunkId();

    Integer getChunkIndex();

    String getSectionPath();

    String getContent();

    Double getScore();

    UUID getDocumentPublicId();

    String getTitle();

    String getBizScope();
}
