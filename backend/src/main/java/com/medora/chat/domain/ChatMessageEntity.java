package com.medora.chat.domain;

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

@Entity
@Table(name = "chat_message")
public class ChatMessageEntity extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversation_id", nullable = false)
    private ConversationEntity conversation;

    @Column(name = "role", nullable = false)
    private String role;

    @Column(name = "message_type", nullable = false)
    private String messageType;

    @Column(name = "content", nullable = false)
    private String content;

    @Column(name = "client_message_id")
    private String clientMessageId;

    @Column(name = "tool_call_json", nullable = false)
    @ColumnTransformer(write = "?::jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String toolCallJson = "{}";

    @Column(name = "citation_json", nullable = false)
    @ColumnTransformer(write = "?::jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String citationJson = "[]";

    @Column(name = "token_usage_json", nullable = false)
    @ColumnTransformer(write = "?::jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String tokenUsageJson = "{}";

    public static ChatMessageEntity userText(ConversationEntity conversation, String content, String clientMessageId) {
        ChatMessageEntity entity = new ChatMessageEntity();
        entity.conversation = conversation;
        entity.role = "USER";
        entity.messageType = "TEXT";
        entity.content = content;
        entity.clientMessageId = clientMessageId;
        return entity;
    }

    public static ChatMessageEntity assistantText(ConversationEntity conversation, String content) {
        ChatMessageEntity entity = new ChatMessageEntity();
        entity.conversation = conversation;
        entity.role = "ASSISTANT";
        entity.messageType = "TEXT";
        entity.content = content;
        return entity;
    }

    public ConversationEntity getConversation() {
        return conversation;
    }

    public String getRole() {
        return role;
    }

    public String getMessageType() {
        return messageType;
    }

    public String getContent() {
        return content;
    }

    public String getClientMessageId() {
        return clientMessageId;
    }

    public String getToolCallJson() {
        return toolCallJson;
    }

    public String getCitationJson() {
        return citationJson;
    }

    public String getTokenUsageJson() {
        return tokenUsageJson;
    }

    public void completeAssistantContent(String content, String tokenUsageJson) {
        this.content = content;
        this.tokenUsageJson = tokenUsageJson == null || tokenUsageJson.isBlank() ? "{}" : tokenUsageJson;
    }
}
