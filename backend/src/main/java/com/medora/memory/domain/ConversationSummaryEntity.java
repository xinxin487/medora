package com.medora.memory.domain;

import com.medora.chat.domain.ConversationEntity;
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

@Entity
@Table(name = "conversation_summary")
public class ConversationSummaryEntity extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversation_id", nullable = false, unique = true)
    private ConversationEntity conversation;

    @Column(name = "summary_text", nullable = false, columnDefinition = "TEXT")
    private String summaryText = "";

    @Column(name = "summary_json", nullable = false)
    @ColumnTransformer(write = "?::jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String summaryJson = "{}";

    @Column(name = "business_state_json", nullable = false)
    @ColumnTransformer(write = "?::jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String businessStateJson = "{}";

    @Column(name = "last_message_count", nullable = false)
    private Integer lastMessageCount = 0;

    @Column(name = "last_summarized_at")
    private OffsetDateTime lastSummarizedAt;

    public static ConversationSummaryEntity create(ConversationEntity conversation) {
        ConversationSummaryEntity entity = new ConversationSummaryEntity();
        entity.conversation = conversation;
        return entity;
    }

    public void refresh(String summaryText,
                        String summaryJson,
                        String businessStateJson,
                        int lastMessageCount,
                        OffsetDateTime lastSummarizedAt) {
        this.summaryText = summaryText == null ? "" : summaryText;
        this.summaryJson = summaryJson == null || summaryJson.isBlank() ? "{}" : summaryJson;
        this.businessStateJson = businessStateJson == null || businessStateJson.isBlank() ? "{}" : businessStateJson;
        this.lastMessageCount = Math.max(0, lastMessageCount);
        this.lastSummarizedAt = lastSummarizedAt;
    }

    public ConversationEntity getConversation() {
        return conversation;
    }

    public String getSummaryText() {
        return summaryText;
    }

    public String getSummaryJson() {
        return summaryJson;
    }

    public String getBusinessStateJson() {
        return businessStateJson;
    }

    public Integer getLastMessageCount() {
        return lastMessageCount;
    }

    public OffsetDateTime getLastSummarizedAt() {
        return lastSummarizedAt;
    }
}
