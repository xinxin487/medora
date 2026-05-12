package com.medora.knowledge.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.ColumnTransformer;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;

@Entity
@Table(name = "knowledge_chunk")
public class KnowledgeChunkEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id", nullable = false)
    private KnowledgeDocumentEntity document;

    @Column(name = "chunk_index", nullable = false)
    private Integer chunkIndex;

    @Column(name = "section_path")
    private String sectionPath;

    @Column(name = "content", nullable = false)
    private String content;

    @Column(name = "embedding_model")
    private String embeddingModel;

    @Column(name = "metadata_json", nullable = false)
    @ColumnTransformer(write = "?::jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String metadataJson = "{}";

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    public static KnowledgeChunkEntity active(KnowledgeDocumentEntity document,
                                              int chunkIndex,
                                              String sectionPath,
                                              String content,
                                              String metadataJson) {
        KnowledgeChunkEntity entity = new KnowledgeChunkEntity();
        entity.document = document;
        entity.chunkIndex = chunkIndex;
        entity.sectionPath = sectionPath;
        entity.content = content;
        entity.metadataJson = metadataJson == null || metadataJson.isBlank() ? "{}" : metadataJson;
        entity.status = "ACTIVE";
        return entity;
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public KnowledgeDocumentEntity getDocument() {
        return document;
    }

    public Integer getChunkIndex() {
        return chunkIndex;
    }

    public String getSectionPath() {
        return sectionPath;
    }

    public String getContent() {
        return content;
    }

    public String getStatus() {
        return status;
    }
}
