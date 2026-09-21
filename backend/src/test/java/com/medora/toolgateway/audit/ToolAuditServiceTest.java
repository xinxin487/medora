package com.medora.toolgateway.audit;

import com.medora.toolgateway.descriptor.ToolDescriptor;
import com.medora.toolgateway.descriptor.ToolRisk;
import com.medora.toolgateway.descriptor.ToolSourceType;
import com.medora.toolgateway.execution.GatewayExecutionContext;
import com.medora.toolgateway.execution.GatewayToolRequest;
import com.medora.toolgateway.execution.GatewayToolResult;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ToolAuditServiceTest {

    @Test
    void recordsSanitizedInvocationWithoutLettingStorageFailureEscape() {
        ToolAuditRepository repository = mock(ToolAuditRepository.class);
        when(repository.save(any(ToolAuditEntity.class))).thenThrow(new IllegalStateException("database offline"));
        ToolAuditService service = new ToolAuditService(repository, new ToolAuditSanitizer());
        ToolDescriptor descriptor = new ToolDescriptor("local.bookAppointment", "1", "", ToolSourceType.LOCAL,
                Map.of(), Map.of(), ToolRisk.HIGH, List.of("USER"), List.of("booking"), Duration.ofSeconds(2), false, true, "SENSITIVE");
        GatewayToolRequest request = new GatewayToolRequest(descriptor.qualifiedName(),
                Map.of("appointmentId", "a-001", "phone", "13800000000"),
                new GatewayExecutionContext(7L, List.of("USER"), "booking", "c-1", "trace-1", "request-1", "confirm-1"));

        service.record(descriptor, request, GatewayToolResult.success(Map.of("ok", true)), 42);

        verify(repository).save(any(ToolAuditEntity.class));
    }

    @Test
    void storesDescriptorOutcomeAndRedactedArguments() {
        ToolAuditRepository repository = mock(ToolAuditRepository.class);
        when(repository.save(any(ToolAuditEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        ToolAuditService service = new ToolAuditService(repository, new ToolAuditSanitizer());
        ToolDescriptor descriptor = new ToolDescriptor("local.searchKnowledge", "1", "", ToolSourceType.LOCAL,
                Map.of(), Map.of(), ToolRisk.LOW, List.of("USER"), List.of("knowledge"), Duration.ofSeconds(2), true, false, "INTERNAL");
        GatewayToolRequest request = new GatewayToolRequest(descriptor.qualifiedName(), Map.of("query", "发热", "email", "patient@example.com"),
                new GatewayExecutionContext(8L, List.of("USER"), "knowledge", "c-2", "trace-2", "request-2", null));

        service.record(descriptor, request,
                GatewayToolResult.failure(GatewayToolResult.Status.DENIED, null, "tool access denied", false), 3);

        var captor = org.mockito.ArgumentCaptor.forClass(ToolAuditEntity.class);
        verify(repository).save(captor.capture());
        ToolAuditEntity audit = captor.getValue();

        assertThat(audit.getToolName()).isEqualTo("local.searchKnowledge");
        assertThat(audit.getToolVersion()).isEqualTo("1");
        assertThat(audit.getStatus()).isEqualTo("DENIED");
        assertThat(audit.getDurationMs()).isEqualTo(3);
        assertThat(audit.getTraceId()).isEqualTo("trace-2");
        assertThat(audit.getArgumentsSummary()).contains("\"email\":\"***\"").doesNotContain("patient@example.com");
        assertThat(audit.getArgumentsHash()).matches("[a-f0-9]{64}");
    }
}
