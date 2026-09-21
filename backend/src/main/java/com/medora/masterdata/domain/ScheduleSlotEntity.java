package com.medora.masterdata.domain;

import com.medora.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Table(name = "schedule_slot")
public class ScheduleSlotEntity extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "department_id", nullable = false)
    private DepartmentEntity department;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "doctor_id", nullable = false)
    private DoctorEntity doctor;

    @Column(name = "hospital_code")
    private String hospitalCode;

    @Column(name = "schedule_date", nullable = false)
    private LocalDate scheduleDate;

    @Column(name = "period_code", nullable = false)
    private String periodCode;

    @Column(name = "start_time")
    private LocalTime startTime;

    @Column(name = "end_time")
    private LocalTime endTime;

    @Column(name = "total_quota", nullable = false)
    private Integer totalQuota;

    @Column(name = "available_quota", nullable = false)
    private Integer availableQuota;

    @Column(name = "reserved_quota", nullable = false)
    private Integer reservedQuota;

    @Column(name = "fee", nullable = false)
    private BigDecimal fee;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "source_type", nullable = false)
    private String sourceType;

    public DepartmentEntity getDepartment() {
        return department;
    }

    public DoctorEntity getDoctor() {
        return doctor;
    }

    public String getHospitalCode() {
        return hospitalCode;
    }

    public LocalDate getScheduleDate() {
        return scheduleDate;
    }

    public String getPeriodCode() {
        return periodCode;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }

    public Integer getTotalQuota() {
        return totalQuota;
    }

    public Integer getAvailableQuota() {
        return availableQuota;
    }

    public Integer getReservedQuota() {
        return reservedQuota;
    }

    public BigDecimal getFee() {
        return fee;
    }

    public String getStatus() {
        return status;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void reserveOne() {
        if (!"OPEN".equals(status) || availableQuota == null || availableQuota <= 0) {
            throw new IllegalStateException("schedule slot is not available");
        }
        availableQuota = availableQuota - 1;
        if (availableQuota == 0) {
            status = "FULL";
        }
    }

    public void releaseOne() {
        if (availableQuota == null || totalQuota == null || availableQuota >= totalQuota) {
            return;
        }
        availableQuota = availableQuota + 1;
        if ("FULL".equals(status) && availableQuota > 0) {
            status = "OPEN";
        }
    }

    public void holdOne() {
        if (!"OPEN".equals(status) || availableQuota == null || availableQuota <= 0) {
            throw new IllegalStateException("schedule slot is not available for hold");
        }
        availableQuota--;
        reservedQuota = reservedQuota == null ? 1 : reservedQuota + 1;
        refreshAvailabilityStatus();
    }

    public void confirmHeldOne() {
        if (reservedQuota == null || reservedQuota <= 0) {
            throw new IllegalStateException("schedule slot has no held quota to confirm");
        }
        reservedQuota--;
        refreshAvailabilityStatus();
    }

    public void releaseHeldOne() {
        if (reservedQuota == null || reservedQuota <= 0) {
            throw new IllegalStateException("schedule slot has no held quota to release");
        }
        reservedQuota--;
        availableQuota = availableQuota == null ? 1 : availableQuota + 1;
        if (totalQuota != null && availableQuota > totalQuota) {
            throw new IllegalStateException("schedule slot quota invariant violated");
        }
        refreshAvailabilityStatus();
    }

    private void refreshAvailabilityStatus() {
        if (availableQuota != null && availableQuota == 0 && "OPEN".equals(status)) {
            status = "FULL";
        } else if (availableQuota != null && availableQuota > 0 && "FULL".equals(status)) {
            status = "OPEN";
        }
    }
}
