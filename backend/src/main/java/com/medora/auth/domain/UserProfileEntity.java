package com.medora.auth.domain;

import com.medora.masterdata.domain.DepartmentEntity;
import org.hibernate.annotations.ColumnTransformer;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "user_profile")
public class UserProfileEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private UserAccountEntity userAccount;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(name = "gender")
    private String gender;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Column(name = "default_hospital_code")
    private String defaultHospitalCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "default_department_id")
    private DepartmentEntity defaultDepartment;

    @Column(name = "preference_json", nullable = false, columnDefinition = "jsonb")
    @ColumnTransformer(write = "?::jsonb")
    private String preferenceJson;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public UserAccountEntity getUserAccount() {
        return userAccount;
    }

    public void setUserAccount(UserAccountEntity userAccount) {
        this.userAccount = userAccount;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getGender() {
        return gender;
    }

    public LocalDate getBirthDate() {
        return birthDate;
    }

    public String getDefaultHospitalCode() {
        return defaultHospitalCode;
    }

    public void setDefaultHospitalCode(String defaultHospitalCode) {
        this.defaultHospitalCode = defaultHospitalCode;
    }

    public DepartmentEntity getDefaultDepartment() {
        return defaultDepartment;
    }

    public void setDefaultDepartment(DepartmentEntity defaultDepartment) {
        this.defaultDepartment = defaultDepartment;
    }

    public String getPreferenceJson() {
        return preferenceJson;
    }

    @PrePersist
    protected void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        if (preferenceJson == null) {
            preferenceJson = "{}";
        }
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
