package com.medora.chat.domain;

import com.medora.auth.domain.UserAccountEntity;
import com.medora.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

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
}
