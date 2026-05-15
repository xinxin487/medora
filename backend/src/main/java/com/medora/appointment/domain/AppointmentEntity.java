package com.medora.appointment.domain;

import com.medora.auth.domain.UserAccountEntity;
import com.medora.common.domain.BaseEntity;
import com.medora.masterdata.domain.DepartmentEntity;
import com.medora.masterdata.domain.DoctorEntity;
import com.medora.masterdata.domain.ScheduleSlotEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "appointment")
public class AppointmentEntity extends BaseEntity {

    @Column(name = "appointment_no", nullable = false, unique = true)
    private String appointmentNo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserAccountEntity userAccount;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "slot_id", nullable = false)
    private ScheduleSlotEntity slot;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "department_id", nullable = false)
    private DepartmentEntity department;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "doctor_id", nullable = false)
    private DoctorEntity doctor;

    @Column(name = "schedule_date", nullable = false)
    private LocalDate scheduleDate;

    @Column(name = "period_code", nullable = false)
    private String periodCode;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "source_channel", nullable = false)
    private String sourceChannel;

    @Column(name = "idempotency_key")
    private String idempotencyKey;

    @Column(name = "booked_at")
    private OffsetDateTime bookedAt;

    @Column(name = "cancelled_at")
    private OffsetDateTime cancelledAt;

    @Column(name = "cancel_reason")
    private String cancelReason;

    @Column(name = "cancel_source")
    private String cancelSource;

    @Column(name = "remark")
    private String remark;

    public static AppointmentEntity confirmed(UserAccountEntity userAccount,
                                              ScheduleSlotEntity slot,
                                              String appointmentNo,
                                              String idempotencyKey,
                                              String remark) {
        return confirmed(userAccount, slot, appointmentNo, idempotencyKey, remark, "WEB");
    }

    public static AppointmentEntity confirmed(UserAccountEntity userAccount,
                                              ScheduleSlotEntity slot,
                                              String appointmentNo,
                                              String idempotencyKey,
                                              String remark,
                                              String sourceChannel) {
        AppointmentEntity entity = new AppointmentEntity();
        entity.appointmentNo = appointmentNo;
        entity.userAccount = userAccount;
        entity.slot = slot;
        entity.department = slot.getDepartment();
        entity.doctor = slot.getDoctor();
        entity.scheduleDate = slot.getScheduleDate();
        entity.periodCode = slot.getPeriodCode();
        entity.status = "CONFIRMED";
        entity.sourceChannel = sourceChannel == null || sourceChannel.isBlank() ? "WEB" : sourceChannel;
        entity.idempotencyKey = idempotencyKey;
        entity.bookedAt = OffsetDateTime.now();
        entity.remark = remark;
        return entity;
    }

    public String getAppointmentNo() {
        return appointmentNo;
    }

    public UserAccountEntity getUserAccount() {
        return userAccount;
    }

    public ScheduleSlotEntity getSlot() {
        return slot;
    }

    public DepartmentEntity getDepartment() {
        return department;
    }

    public DoctorEntity getDoctor() {
        return doctor;
    }

    public LocalDate getScheduleDate() {
        return scheduleDate;
    }

    public String getPeriodCode() {
        return periodCode;
    }

    public String getStatus() {
        return status;
    }

    public String getSourceChannel() {
        return sourceChannel;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public OffsetDateTime getBookedAt() {
        return bookedAt;
    }

    public OffsetDateTime getCancelledAt() {
        return cancelledAt;
    }

    public String getCancelReason() {
        return cancelReason;
    }

    public String getCancelSource() {
        return cancelSource;
    }

    public String getRemark() {
        return remark;
    }

    public boolean belongsTo(Long userId) {
        return userAccount != null && userAccount.getId() != null && userAccount.getId().equals(userId);
    }

    public void cancel(String reason) {
        cancel(reason, "WEB");
    }

    public void cancel(String reason, String source) {
        if (!"CONFIRMED".equals(status)) {
            throw new IllegalStateException("appointment status is not cancellable");
        }
        status = "CANCELLED";
        cancelledAt = OffsetDateTime.now();
        cancelReason = reason;
        cancelSource = source == null || source.isBlank() ? "WEB" : source;
    }
}
