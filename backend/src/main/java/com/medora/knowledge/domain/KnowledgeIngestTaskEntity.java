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

import java.time.OffsetDateTime;

@Entity
@Table(name = "knowledge_ingest_task")
public class KnowledgeIngestTaskEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id", nullable = false)
    private KnowledgeDocumentEntity document;

    @Column(name = "task_type", nullable = false)
    private String taskType;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "retry_count", nullable = false)
    private Integer retryCount = 0;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "started_at")
    private OffsetDateTime startedAt;

    @Column(name = "finished_at")
    private OffsetDateTime finishedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    public static KnowledgeIngestTaskEntity running(KnowledgeDocumentEntity document, String taskType) {
        KnowledgeIngestTaskEntity entity = new KnowledgeIngestTaskEntity();
        entity.document = document;
        entity.taskType = taskType;
        entity.status = "RUNNING";
        entity.startedAt = OffsetDateTime.now();
        return entity;
    }

    public void markSuccess() {
        this.status = "SUCCESS";
        this.finishedAt = OffsetDateTime.now();
    }

    public void markFailed(String errorMessage) {
        this.status = "FAILED";
        this.errorMessage = errorMessage == null ? null : errorMessage.substring(0, Math.min(500, errorMessage.length()));
        this.finishedAt = OffsetDateTime.now();
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }
}
