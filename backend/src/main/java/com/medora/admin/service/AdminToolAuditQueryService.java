package com.medora.admin.service;

import com.medora.admin.api.response.AdminToolAuditItem;
import com.medora.common.api.PageResponse;
import com.medora.toolgateway.audit.ToolAuditEntity;
import com.medora.toolgateway.audit.ToolAuditRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;

@Service
public class AdminToolAuditQueryService {
    private final ToolAuditRepository repository;
    public AdminToolAuditQueryService(ToolAuditRepository repository) { this.repository = repository; }

    @Transactional(readOnly = true)
    public PageResponse<AdminToolAuditItem> list(String toolName, String status, Integer pageNo, Integer pageSize) {
        int normalizedPageNo = pageNo == null || pageNo < 1 ? 1 : pageNo;
        int normalizedPageSize = pageSize == null || pageSize < 1 ? 20 : Math.min(pageSize, 100);
        return PageResponse.from(repository.findAll(specification(toolName, status),
                PageRequest.of(normalizedPageNo - 1, normalizedPageSize, Sort.by(Sort.Order.desc("createdAt"))))
                .map(this::toItem));
    }

    private Specification<ToolAuditEntity> specification(String toolName, String status) {
        return (root, query, builder) -> {
            var predicates = new ArrayList<Predicate>();
            if (StringUtils.hasText(toolName)) predicates.add(builder.like(builder.lower(root.get("toolName")), "%" + toolName.trim().toLowerCase() + "%"));
            if (StringUtils.hasText(status)) predicates.add(builder.equal(root.get("status"), status.trim().toUpperCase()));
            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private AdminToolAuditItem toItem(ToolAuditEntity entity) {
        return new AdminToolAuditItem(entity.getPublicId().toString(), entity.getToolName(), entity.getToolVersion(),
                entity.getSourceType(), entity.getRiskLevel(), entity.getAgentName(), entity.getConversationId(),
                entity.getTraceId(), entity.getRequestId(), entity.getStatus(), entity.getErrorCode(), entity.getDurationMs(),
                entity.getArgumentsHash(), entity.getArgumentsSummary(), entity.getCreatedAt());
    }
}
