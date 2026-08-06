package com.medora.chat.domain;

import com.medora.auth.domain.UserAccountEntity;
import com.medora.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.Map;

@Entity
@Table(name = "conversation")
public class ConversationEntity extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserAccountEntity userAccount;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "scene_type", nullable = false)
    private String sceneType;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "last_message_at")
    private OffsetDateTime lastMessageAt;

    /** Phase 9.3: ADK Session state 的 JSON 序列化形式,允许 NULL(旧 conversation 记录无此字段) */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "session_state", columnDefinition = "jsonb")
    private Map<String, Object> sessionState;

    public static ConversationEntity create(UserAccountEntity userAccount, String title, String sceneType) {
        ConversationEntity entity = new ConversationEntity();
        entity.userAccount = userAccount;
        entity.title = title;
        entity.sceneType = sceneType;
        entity.status = "ACTIVE";
        return entity;
    }

    public UserAccountEntity getUserAccount() {
        return userAccount;
    }

    public String getTitle() {
        return title;
    }

    public String getSceneType() {
        return sceneType;
    }

    public String getStatus() {
        return status;
    }

    public OffsetDateTime getLastMessageAt() {
        return lastMessageAt;
    }

    public void touchLastMessageAt(OffsetDateTime lastMessageAt) {
        this.lastMessageAt = lastMessageAt;
    }

    public void markDeleted() {
        this.status = "DELETED";
    }

    public Map<String, Object> getSessionState() {
        return sessionState;
    }

    public void setSessionState(Map<String, Object> sessionState) {
        this.sessionState = sessionState;
    }
}
