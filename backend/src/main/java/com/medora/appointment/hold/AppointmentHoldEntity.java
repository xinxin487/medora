package com.medora.appointment.hold;

import com.medora.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;

@Entity
@Table(name = "appointment_hold")
public class AppointmentHoldEntity extends BaseEntity {

    @Column(name = "user_id", nullable = false) private Long userId;
    @Column(name = "slot_id", nullable = false) private Long slotId;
    @Column(name = "idempotency_key", nullable = false) private String idempotencyKey;
    @Column(name = "status", nullable = false) private String status;
    @Column(name = "expires_at", nullable = false) private OffsetDateTime expiresAt;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "snapshot_json", nullable = false, columnDefinition = "jsonb")
    private String snapshotJson = "{}";
    @Column(name = "confirmed_appointment_id") private Long confirmedAppointmentId;

    public static AppointmentHoldEntity pending(Long userId, Long slotId, String idempotencyKey, OffsetDateTime expiresAt) {
        AppointmentHoldEntity hold = new AppointmentHoldEntity();
        hold.userId = userId;
        hold.slotId = slotId;
        hold.idempotencyKey = idempotencyKey;
        hold.status = "PENDING_CONFIRMATION";
        hold.expiresAt = expiresAt;
        return hold;
    }

    public void confirm(Long appointmentId) {
        transition("CONFIRMED");
        confirmedAppointmentId = appointmentId;
    }

    public void confirm() { transition("CONFIRMED"); }
    public void cancel() { transition("CANCELLED"); }
    public void expire() { transition("EXPIRED"); }
    private void transition(String target) {
        if (!"PENDING_CONFIRMATION".equals(status)) throw new IllegalStateException("appointment hold is not pending confirmation");
        status = target;
    }
    public Long getUserId() { return userId; }
    public Long getSlotId() { return slotId; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getStatus() { return status; }
    public OffsetDateTime getExpiresAt() { return expiresAt; }
    public String getSnapshotJson() { return snapshotJson; }
    public Long getConfirmedAppointmentId() { return confirmedAppointmentId; }
}
