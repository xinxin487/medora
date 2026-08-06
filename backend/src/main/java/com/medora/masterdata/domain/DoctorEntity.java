package com.medora.masterdata.domain;

import com.medora.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(name = "doctor")
public class DoctorEntity extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "department_id", nullable = false)
    private DepartmentEntity department;

    @Column(name = "code", unique = true)
    private String code;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "title")
    private String title;

    @Column(name = "specialty")
    private String specialty;

    @Column(name = "intro")
    private String intro;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "consultation_fee")
    private BigDecimal consultationFee;

    public DepartmentEntity getDepartment() {
        return department;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getTitle() {
        return title;
    }

    public String getSpecialty() {
        return specialty;
    }

    public String getIntro() {
        return intro;
    }

    public String getStatus() {
        return status;
    }

    public BigDecimal getConsultationFee() {
        return consultationFee;
    }
}
