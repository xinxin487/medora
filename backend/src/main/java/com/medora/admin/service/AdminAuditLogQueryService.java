package com.medora.admin.service;

import com.medora.admin.api.response.AdminAuditLogItem;
import com.medora.auth.domain.UserAccountEntity;
import com.medora.common.api.PageResponse;
import com.medora.common.audit.AuditLogEntity;
import com.medora.common.audit.AuditLogRepository;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Service
public class AdminAuditLogQueryService {

    private final AuditLogRepository auditLogRepository;

    public AdminAuditLogQueryService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminAuditLogItem> list(String operatorType,
                                                String actionType,
                                                String resourceType,
                                                String requestId,
                                                Integer pageNo,
                                                Integer pageSize) {
        Page<AdminAuditLogItem> page = auditLogRepository.findAll(
                toSpec(operatorType, actionType, resourceType, requestId),
                PageRequest.of(
                        normalizePageNo(pageNo) - 1,
                        normalizePageSize(pageSize),
                        Sort.by(Sort.Order.desc("createdAt"))
                )
        ).map(this::toItem);
        return PageResponse.from(page);
    }

    private Specification<AuditLogEntity> toSpec(String operatorType,
                                                 String actionType,
                                                 String resourceType,
                                                 String requestId) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            Join<AuditLogEntity, UserAccountEntity> userJoin = root.join("userAccount", JoinType.LEFT);
            if (StringUtils.hasText(operatorType)) {
                predicates.add(builder.equal(root.get("operatorType"), operatorType.trim().toUpperCase()));
            }
            if (StringUtils.hasText(actionType)) {
                predicates.add(builder.equal(root.get("actionType"), actionType.trim().toUpperCase()));
            }
            if (StringUtils.hasText(resourceType)) {
                predicates.add(builder.equal(root.get("resourceType"), resourceType.trim().toUpperCase()));
            }
            if (StringUtils.hasText(requestId)) {
                String like = "%" + requestId.trim().toLowerCase() + "%";
                predicates.add(builder.or(
                        builder.like(builder.lower(root.get("requestId")), like),
                        builder.like(builder.lower(userJoin.get("username")), like),
                        builder.like(builder.lower(userJoin.get("mobile")), like),
                        builder.like(builder.lower(userJoin.get("email")), like)
                ));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private AdminAuditLogItem toItem(AuditLogEntity entity) {
        UserAccountEntity userAccount = entity.getUserAccount();
        return new AdminAuditLogItem(
                entity.getPublicId().toString(),
                userAccount == null ? null : userAccount.getPublicId().toString(),
                resolveUserLabel(userAccount),
                entity.getOperatorType(),
                entity.getActionType(),
                entity.getResourceType(),
                entity.getResourceId(),
                entity.getRequestId(),
                entity.getDetailJson(),
                entity.getCreatedAt()
        );
    }

    private String resolveUserLabel(UserAccountEntity userAccount) {
        if (userAccount == null) {
            return "SYSTEM";
        }
        if (StringUtils.hasText(userAccount.getUsername())) {
            return userAccount.getUsername().trim();
        }
        if (StringUtils.hasText(userAccount.getMobile())) {
            return userAccount.getMobile().trim();
        }
        if (StringUtils.hasText(userAccount.getEmail())) {
            return userAccount.getEmail().trim();
        }
        return userAccount.getPublicId().toString();
    }

    private int normalizePageNo(Integer pageNo) {
        return pageNo == null || pageNo < 1 ? 1 : pageNo;
    }

    private int normalizePageSize(Integer pageSize) {
        return pageSize == null || pageSize < 1 ? 20 : Math.min(pageSize, 100);
    }
}
