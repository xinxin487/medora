package com.medora.common.audit;

import com.medora.auth.domain.UserAccountEntity;
import com.medora.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.ColumnTransformer;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "audit_log")
public class AuditLogEntity extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private UserAccountEntity userAccount;

    @Column(name = "operator_type", nullable = false)
    private String operatorType;

    @Column(name = "action_type", nullable = false)
    private String actionType;

    @Column(name = "resource_type", nullable = false)
    private String resourceType;

    @Column(name = "resource_id")
    private String resourceId;

    @Column(name = "request_id")
    private String requestId;

    @Column(name = "detail_json", nullable = false)
    @ColumnTransformer(write = "?::jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String detailJson = "{}";

    public static AuditLogEntity of(UserAccountEntity userAccount,
                                    String operatorType,
                                    String actionType,
                                    String resourceType,
                                    String resourceId,
                                    String requestId,
                                    String detailJson) {
        AuditLogEntity entity = new AuditLogEntity();
        entity.userAccount = userAccount;
        entity.operatorType = operatorType;
        entity.actionType = actionType;
        entity.resourceType = resourceType;
        entity.resourceId = resourceId;
        entity.requestId = requestId;
        entity.detailJson = detailJson == null || detailJson.isBlank() ? "{}" : detailJson;
        return entity;
    }

    public UserAccountEntity getUserAccount() {
        return userAccount;
    }

    public String getOperatorType() {
        return operatorType;
    }

    public String getActionType() {
        return actionType;
    }

    public String getResourceType() {
        return resourceType;
    }

    public String getResourceId() {
        return resourceId;
    }

    public String getRequestId() {
        return requestId;
    }

    public String getDetailJson() {
        return detailJson;
    }
}
