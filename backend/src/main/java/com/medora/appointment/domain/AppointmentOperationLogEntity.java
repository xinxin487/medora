package com.medora.appointment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.ColumnTransformer;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;

@Entity
@Table(name = "appointment_operation_log")
public class AppointmentOperationLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "appointment_id", nullable = false)
    private AppointmentEntity appointment;

    @Column(name = "operation_type", nullable = false)
    private String operationType;

    @Column(name = "operator_type", nullable = false)
    private String operatorType;

    @Column(name = "operator_id")
    private Long operatorId;

    @Column(name = "request_id")
    private String requestId;

    @Column(name = "before_status")
    private String beforeStatus;

    @Column(name = "after_status")
    private String afterStatus;

    @Column(name = "detail_json", nullable = false)
    @ColumnTransformer(write = "?::jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String detailJson = "{}";

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    public static AppointmentOperationLogEntity of(AppointmentEntity appointment,
                                                   String operationType,
                                                   Long operatorId,
                                                   String requestId,
                                                   String beforeStatus,
                                                   String afterStatus,
                                                   String detailJson) {
        AppointmentOperationLogEntity entity = new AppointmentOperationLogEntity();
        entity.appointment = appointment;
        entity.operationType = operationType;
        entity.operatorType = "USER";
        entity.operatorId = operatorId;
        entity.requestId = requestId;
        entity.beforeStatus = beforeStatus;
        entity.afterStatus = afterStatus;
        entity.detailJson = detailJson == null || detailJson.isBlank() ? "{}" : detailJson;
        return entity;
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }
}
