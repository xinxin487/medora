package com.medora.a2ui.domain;

import com.medora.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

@Entity
@Table(name = "a2ui_action")
public class A2uiActionEntity extends BaseEntity {
    @Column(name = "hold_id", nullable = false) private Long holdId;
    @Column(name = "user_id", nullable = false) private Long userId;
    @Column(name = "surface_id", nullable = false) private String surfaceId;
    @Column(name = "action_type", nullable = false) private String actionType;
    @Column(name = "token_hash", nullable = false) private String tokenHash;
    @Column(name = "status", nullable = false) private String status;
    @Column(name = "expires_at", nullable = false) private OffsetDateTime expiresAt;
    @Column(name = "consumed_at") private OffsetDateTime consumedAt;

    public static A2uiActionEntity pending(Long holdId, Long userId, String surfaceId, String actionType,
                                           String tokenHash, OffsetDateTime expiresAt) {
        A2uiActionEntity action = new A2uiActionEntity();
        action.holdId = holdId; action.userId = userId; action.surfaceId = surfaceId;
        action.actionType = actionType; action.tokenHash = tokenHash; action.status = "PENDING"; action.expiresAt = expiresAt;
        return action;
    }
    public void consume(OffsetDateTime at) { requirePending(); status = "CONSUMED"; consumedAt = at; }
    public void cancel() { requirePending(); status = "CANCELLED"; }
    public void expire() { requirePending(); status = "EXPIRED"; }
    private void requirePending() { if (!"PENDING".equals(status)) throw new IllegalStateException("a2ui action is no longer pending"); }
    public Long getHoldId() { return holdId; }
    public Long getUserId() { return userId; }
    public String getSurfaceId() { return surfaceId; }
    public String getActionType() { return actionType; }
    public String getTokenHash() { return tokenHash; }
    public String getStatus() { return status; }
    public OffsetDateTime getExpiresAt() { return expiresAt; }
    public OffsetDateTime getConsumedAt() { return consumedAt; }
}
