package com.medora.common.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medora.auth.domain.UserAccountEntity;
import com.medora.common.metrics.MedoraMetricsService;
import com.medora.common.web.RequestMetadata;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;
    private final MedoraMetricsService medoraMetricsService;

    public AuditLogService(AuditLogRepository auditLogRepository,
                           ObjectMapper objectMapper,
                           MedoraMetricsService medoraMetricsService) {
        this.auditLogRepository = auditLogRepository;
        this.objectMapper = objectMapper;
        this.medoraMetricsService = medoraMetricsService;
    }

    @Transactional
    public void record(UserAccountEntity userAccount,
                       String operatorType,
                       String actionType,
                       String resourceType,
                       String resourceId,
                       Object detail) {
        AuditLogEntity entity = auditLogRepository.save(AuditLogEntity.of(
                userAccount,
                normalize(operatorType, "SYSTEM"),
                normalize(actionType, "UNKNOWN"),
                normalize(resourceType, "UNKNOWN"),
                resourceId,
                RequestMetadata.currentRequestId(),
                toJson(detail)
        ));
        medoraMetricsService.recordAuditEvent(entity.getOperatorType(), entity.getActionType(), entity.getResourceType());
    }

    private String toJson(Object detail) {
        if (detail == null) {
            return "{}";
        }
        try {
            return objectMapper.writeValueAsString(detail);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("audit detail serialization failed", ex);
        }
    }

    private String normalize(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim().toUpperCase();
    }
}
