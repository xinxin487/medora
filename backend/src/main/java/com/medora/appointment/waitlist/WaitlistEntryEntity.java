package com.medora.appointment.waitlist;

import com.medora.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

@Entity
@Table(name = "waitlist_entry")
public class WaitlistEntryEntity extends BaseEntity {
    @Column(name = "user_id", nullable = false) private Long userId;
    @Column(name = "slot_id", nullable = false) private Long slotId;
    @Column(name = "status", nullable = false) private String status;
    @Column(name = "priority", nullable = false) private int priority;
    @Column(name = "offered_hold_id") private Long offeredHoldId;
    @Column(name = "joined_at", nullable = false) private OffsetDateTime joinedAt;

    public static WaitlistEntryEntity waiting(Long userId, Long slotId, int priority, OffsetDateTime joinedAt) {
        WaitlistEntryEntity entry = new WaitlistEntryEntity();
        entry.userId = userId; entry.slotId = slotId; entry.priority = priority; entry.joinedAt = joinedAt; entry.status = "WAITING";
        return entry;
    }
    public void offer(Long holdId) { require("WAITING"); status = "OFFERED"; offeredHoldId = holdId; }
    public void confirm() { require("OFFERED"); status = "CONFIRMED"; }
    public void cancel() { if (!"WAITING".equals(status) && !"OFFERED".equals(status)) throw new IllegalStateException("waitlist entry is not active"); status = "CANCELLED"; }
    public void expireOffer() { require("OFFERED"); status = "EXPIRED"; }
    private void require(String expected) { if (!expected.equals(status)) throw new IllegalStateException("waitlist entry state transition is invalid"); }
    public Long getUserId() { return userId; }
    public Long getSlotId() { return slotId; }
    public String getStatus() { return status; }
    public int getPriority() { return priority; }
    public Long getOfferedHoldId() { return offeredHoldId; }
    public OffsetDateTime getJoinedAt() { return joinedAt; }
}
