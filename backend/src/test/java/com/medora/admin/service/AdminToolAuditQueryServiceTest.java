package com.medora.admin.service;

import com.medora.common.api.PageResponse;
import com.medora.toolgateway.audit.ToolAuditEntity;
import com.medora.toolgateway.audit.ToolAuditRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdminToolAuditQueryServiceTest {

    @Test
    void returnsOnlyTheSanitizedAuditSummaryForAdministrators() {
        ToolAuditRepository repository = mock(ToolAuditRepository.class);
        ToolAuditEntity entity = new ToolAuditEntity();
        ReflectionTestUtils.setField(entity, "publicId", UUID.fromString("00000000-0000-0000-0000-000000000019"));
        ReflectionTestUtils.setField(entity, "toolName", "local.bookAppointment");
        ReflectionTestUtils.setField(entity, "toolVersion", "1");
        ReflectionTestUtils.setField(entity, "status", "SUCCESS");
        ReflectionTestUtils.setField(entity, "durationMs", 21L);
        ReflectionTestUtils.setField(entity, "argumentsSummary", "{\"phone\":\"***\",\"slotId\":\"s-001\"}");
        when(repository.findAll(any(org.springframework.data.jpa.domain.Specification.class), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(entity)));

        PageResponse<?> page = new AdminToolAuditQueryService(repository).list("local.book", "SUCCESS", 1, 20);

        assertThat(page.list()).hasSize(1);
        assertThat(page.list().getFirst().toString()).contains("local.bookAppointment", "\"phone\":\"***\"")
                .doesNotContain("13800000000");
    }
}
