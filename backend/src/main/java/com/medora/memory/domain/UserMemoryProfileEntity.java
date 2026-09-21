package com.medora.memory.domain;

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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;

@Entity
@Table(name = "user_memory_profile")
public class UserMemoryProfileEntity extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserAccountEntity userAccount;

    @Column(name = "memory_type", nullable = false)
    private String memoryType;

    @Column(name = "memory_key", nullable = false)
    private String memoryKey;

    @Column(name = "summary", nullable = false)
    private String summary;

    @Column(name = "value_json", nullable = false)
    @ColumnTransformer(write = "?::jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String valueJson = "{}";

    @Column(name = "confidence", nullable = false, precision = 5, scale = 2)
    private BigDecimal confidence = BigDecimal.ZERO;

    @Column(name = "source", nullable = false)
    private String source;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "last_confirmed_at", nullable = false)
    private OffsetDateTime lastConfirmedAt;

    @Column(name = "last_accessed_at")
    private OffsetDateTime lastAccessedAt;

    @Column(name = "expires_at")
    private OffsetDateTime expiresAt;

    @Column(name = "scope_key", nullable = false)
    private String scopeKey = "GLOBAL";

    @Column(name = "normalized_value")
    private String normalizedValue;

    @Column(name = "fingerprint", nullable = false)
    private String fingerprint = "legacy";

    @Column(name = "evidence_count", nullable = false)
    private Integer evidenceCount = 1;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "superseded_by_id")
    private UserMemoryProfileEntity supersededBy;

    public static UserMemoryProfileEntity create(UserAccountEntity userAccount,
                                                 String memoryType,
                                                 String memoryKey,
                                                 String summary,
                                                 String valueJson,
                                                 double confidence,
                                                 String source,
                                                 OffsetDateTime confirmedAt,
                                                 OffsetDateTime expiresAt) {
        UserMemoryProfileEntity entity = new UserMemoryProfileEntity();
        entity.userAccount = userAccount;
        entity.memoryType = memoryType;
        entity.memoryKey = memoryKey;
        entity.refresh(summary, valueJson, confidence, source, confirmedAt, expiresAt);
        entity.normalizedValue = entity.summary.toLowerCase(java.util.Locale.ROOT).trim();
        return entity;
    }

    public static final String STATUS_CANDIDATE = "CANDIDATE";
    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_INACTIVE = "INACTIVE";

    public void refresh(String summary,
                        String valueJson,
                        double confidence,
                        String source,
                        OffsetDateTime confirmedAt,
                        OffsetDateTime expiresAt) {
        this.summary = summary == null ? "" : summary;
        this.valueJson = valueJson == null || valueJson.isBlank() ? "{}" : valueJson;
        this.confidence = BigDecimal.valueOf(confidence).setScale(2, RoundingMode.HALF_UP);
        this.source = source == null ? "UNKNOWN" : source;
        this.status = STATUS_ACTIVE;
        this.lastConfirmedAt = confirmedAt == null ? OffsetDateTime.now() : confirmedAt;
        this.lastAccessedAt = this.lastConfirmedAt;
        this.expiresAt = expiresAt;
    }

    public void refreshAsCandidate(String summary,
                                   String valueJson,
                                   double confidence,
                                   String source,
                                   OffsetDateTime confirmedAt,
                                   OffsetDateTime expiresAt) {
        this.summary = summary == null ? "" : summary;
        this.valueJson = valueJson == null || valueJson.isBlank() ? "{}" : valueJson;
        this.confidence = BigDecimal.valueOf(confidence).setScale(2, RoundingMode.HALF_UP);
        this.source = source == null ? "UNKNOWN" : source;
        this.status = STATUS_CANDIDATE;
        this.lastConfirmedAt = confirmedAt == null ? OffsetDateTime.now() : confirmedAt;
        this.lastAccessedAt = this.lastConfirmedAt;
        this.expiresAt = expiresAt;
    }

    public boolean isCandidate() {
        return STATUS_CANDIDATE.equals(this.status);
    }

    public boolean isActive() {
        return STATUS_ACTIVE.equals(this.status);
    }

    public void promoteToActive(OffsetDateTime confirmedAt) {
        this.status = STATUS_ACTIVE;
        this.lastConfirmedAt = confirmedAt == null ? OffsetDateTime.now() : confirmedAt;
    }

    public void markAccessed(OffsetDateTime accessedAt) {
        this.lastAccessedAt = accessedAt == null ? OffsetDateTime.now() : accessedAt;
    }

    public void expire() {
        this.status = STATUS_INACTIVE;
        this.expiresAt = OffsetDateTime.now();
    }

    public void confirmEvidence(OffsetDateTime confirmedAt) {
        this.evidenceCount = (this.evidenceCount == null ? 0 : this.evidenceCount) + 1;
        this.lastConfirmedAt = confirmedAt == null ? OffsetDateTime.now() : confirmedAt;
        this.lastAccessedAt = this.lastConfirmedAt;
        this.status = STATUS_ACTIVE;
    }

    public void supersedeWith(UserMemoryProfileEntity winner) {
        this.supersededBy = winner;
        this.status = STATUS_INACTIVE;
    }

    public boolean isExpired(OffsetDateTime now) {
        return expiresAt != null && !expiresAt.isAfter(now == null ? OffsetDateTime.now() : now);
    }

    public UserAccountEntity getUserAccount() {
        return userAccount;
    }

    public String getMemoryType() {
        return memoryType;
    }

    public String getMemoryKey() {
        return memoryKey;
    }

    public String getSummary() {
        return summary;
    }

    public String getValueJson() {
        return valueJson;
    }

    public BigDecimal getConfidence() {
        return confidence;
    }

    public String getSource() {
        return source;
    }

    public String getStatus() {
        return status;
    }

    public OffsetDateTime getLastConfirmedAt() {
        return lastConfirmedAt;
    }

    public OffsetDateTime getLastAccessedAt() {
        return lastAccessedAt;
    }

    public OffsetDateTime getExpiresAt() {
        return expiresAt;
    }

    public String getScopeKey() { return scopeKey; }
    public String getNormalizedValue() { return normalizedValue; }
    public String getFingerprint() { return fingerprint; }
    public Integer getEvidenceCount() { return evidenceCount; }
    public UserMemoryProfileEntity getSupersededBy() { return supersededBy; }
}
