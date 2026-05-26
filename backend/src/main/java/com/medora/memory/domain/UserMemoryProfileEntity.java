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
        return entity;
    }

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
        this.status = "ACTIVE";
        this.lastConfirmedAt = confirmedAt == null ? OffsetDateTime.now() : confirmedAt;
        this.lastAccessedAt = this.lastConfirmedAt;
        this.expiresAt = expiresAt;
    }

    public void markAccessed(OffsetDateTime accessedAt) {
        this.lastAccessedAt = accessedAt == null ? OffsetDateTime.now() : accessedAt;
    }

    public void expire() {
        this.status = "INACTIVE";
        this.expiresAt = OffsetDateTime.now();
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
}
