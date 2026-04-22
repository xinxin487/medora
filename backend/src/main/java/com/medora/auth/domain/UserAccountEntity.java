package com.medora.auth.domain;

import com.medora.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

@Entity
@Table(name = "user_account")
public class UserAccountEntity extends BaseEntity {

    @Column(name = "username")
    private String username;

    @Column(name = "mobile")
    private String mobile;

    @Column(name = "email")
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "register_source", nullable = false)
    private String registerSource;

    @Column(name = "last_login_at")
    private OffsetDateTime lastLoginAt;

    @Column(name = "last_login_ip")
    private String lastLoginIp;

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getMobile() {
        return mobile;
    }

    public void setMobile(String mobile) {
        this.mobile = mobile;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getRegisterSource() {
        return registerSource;
    }

    public void setRegisterSource(String registerSource) {
        this.registerSource = registerSource;
    }

    public OffsetDateTime getLastLoginAt() {
        return lastLoginAt;
    }

    public String getLastLoginIp() {
        return lastLoginIp;
    }

    public void markLoggedIn(String clientIp) {
        this.lastLoginAt = OffsetDateTime.now();
        this.lastLoginIp = clientIp;
    }
}
