package com.medora.knowledge.domain;

import com.medora.auth.domain.UserAccountEntity;
import com.medora.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.ColumnTransformer;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;

@Entity
@Table(name = "knowledge_document")
public class KnowledgeDocumentEntity extends BaseEntity {

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "source_type", nullable = false)
    private String sourceType;

    @Column(name = "source_uri")
    private String sourceUri;

    @Column(name = "biz_scope", nullable = false)
    private String bizScope;

    @Column(name = "department_tags", nullable = false, columnDefinition = "text[]")
    @JdbcTypeCode(SqlTypes.ARRAY)
    private String[] departmentTags = new String[0];

    @Column(name = "doctor_tags", nullable = false, columnDefinition = "text[]")
    @JdbcTypeCode(SqlTypes.ARRAY)
    private String[] doctorTags = new String[0];

    @Column(name = "version_no", nullable = false)
    private Integer versionNo = 1;

    @Column(name = "publish_status", nullable = false)
    private String publishStatus;

    @Column(name = "ingest_status", nullable = false)
    private String ingestStatus;

    @Column(name = "content_hash")
    private String contentHash;

    @Column(name = "metadata_json", nullable = false)
    @ColumnTransformer(write = "?::jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String metadataJson = "{}";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploaded_by")
    private UserAccountEntity uploadedBy;

    @Column(name = "uploaded_at", nullable = false)
    private OffsetDateTime uploadedAt;

    public static KnowledgeDocumentEntity uploaded(String title,
                                                   String sourceType,
                                                   String sourceUri,
                                                   String bizScope,
                                                   List<String> departmentTags,
                                                   List<String> doctorTags,
                                                   String contentHash,
                                                   String metadataJson,
                                                   UserAccountEntity uploadedBy) {
        KnowledgeDocumentEntity entity = new KnowledgeDocumentEntity();
        entity.title = title;
        entity.sourceType = sourceType;
        entity.sourceUri = sourceUri;
        entity.bizScope = bizScope;
        entity.departmentTags = departmentTags.toArray(String[]::new);
        entity.doctorTags = doctorTags.toArray(String[]::new);
        entity.publishStatus = "DRAFT";
        entity.ingestStatus = "UPLOADED";
        entity.contentHash = contentHash;
        entity.metadataJson = metadataJson == null || metadataJson.isBlank() ? "{}" : metadataJson;
        entity.uploadedBy = uploadedBy;
        entity.uploadedAt = OffsetDateTime.now();
        return entity;
    }

    public void markIndexed() {
        this.ingestStatus = "INDEXED";
    }

    public void markIngestFailed(String errorMessage) {
        this.ingestStatus = "FAILED";
        this.metadataJson = "{\"error\":\"" + sanitizeJsonValue(errorMessage) + "\"}";
    }

    public void publish() {
        this.publishStatus = "PUBLISHED";
    }

    public void offline() {
        this.publishStatus = "OFFLINE";
    }

    public String getTitle() {
        return title;
    }

    public String getSourceType() {
        return sourceType;
    }

    public String getSourceUri() {
        return sourceUri;
    }

    public String getBizScope() {
        return bizScope;
    }

    public List<String> getDepartmentTags() {
        return Arrays.asList(departmentTags);
    }

    public List<String> getDoctorTags() {
        return Arrays.asList(doctorTags);
    }

    public Integer getVersionNo() {
        return versionNo;
    }

    public String getPublishStatus() {
        return publishStatus;
    }

    public String getIngestStatus() {
        return ingestStatus;
    }

    public String getContentHash() {
        return contentHash;
    }

    public String getMetadataJson() {
        return metadataJson;
    }

    public OffsetDateTime getUploadedAt() {
        return uploadedAt;
    }

    private static String sanitizeJsonValue(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
