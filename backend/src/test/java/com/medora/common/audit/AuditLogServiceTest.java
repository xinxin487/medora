package com.medora.common.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medora.auth.domain.UserAccountEntity;
import com.medora.common.metrics.MedoraMetricsService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuditLogServiceTest {

    @Test
    void shouldSaveAuditLogAndRecordMetric() {
        AuditLogRepository repository = mock(AuditLogRepository.class);
        MedoraMetricsService medoraMetricsService = mock(MedoraMetricsService.class);
        AuditLogService service = new AuditLogService(repository, new ObjectMapper(), medoraMetricsService);

        UserAccountEntity user = new UserAccountEntity();
        ReflectionTestUtils.setField(user, "publicId", UUID.randomUUID());
        user.setStatus("ACTIVE");
        user.setRegisterSource("WEB");

        when(repository.save(any(AuditLogEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.record(user, "ADMIN", "KB_DOCUMENT_UPLOAD", "KNOWLEDGE_DOCUMENT", "doc-001",
                Map.of("title", "挂号规则"));

        ArgumentCaptor<AuditLogEntity> captor = ArgumentCaptor.forClass(AuditLogEntity.class);
        verify(repository).save(captor.capture());
        verify(medoraMetricsService).recordAuditEvent("ADMIN", "KB_DOCUMENT_UPLOAD", "KNOWLEDGE_DOCUMENT");

        AuditLogEntity saved = captor.getValue();
        assertEquals("doc-001", saved.getResourceId());
        assertEquals("unknown-request-id", saved.getRequestId());
        assertTrue(saved.getDetailJson().contains("挂号规则"));
    }
}
