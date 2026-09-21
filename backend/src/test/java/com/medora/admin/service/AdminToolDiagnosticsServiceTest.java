package com.medora.admin.service;

import com.medora.toolgateway.audit.ToolAuditEntity;
import com.medora.toolgateway.audit.ToolAuditRepository;
import com.medora.toolgateway.descriptor.ToolDescriptor;
import com.medora.toolgateway.descriptor.ToolRisk;
import com.medora.toolgateway.descriptor.ToolSourceType;
import com.medora.toolgateway.execution.ToolCircuitBreakerRegistry;
import com.medora.toolgateway.registry.GatewayToolRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdminToolDiagnosticsServiceTest {

    @Test
    void summarizesDescriptorCircuitStateAndRecentSuccessMetricsWithoutArguments() {
        ToolDescriptor descriptor = new ToolDescriptor("local.searchKnowledge", "1", "search", ToolSourceType.LOCAL,
                Map.of("type", "object"), Map.of("type", "object"), ToolRisk.LOW, List.of("USER"),
                List.of("knowledge", "triage"), Duration.ofSeconds(3), true, false, "INTERNAL");
        ToolAuditRepository repository = mock(ToolAuditRepository.class);
        when(repository.findTop500ByToolNameOrderByCreatedAtDesc("local.searchKnowledge"))
                .thenReturn(List.of(audit("SUCCESS", 10), audit("SUCCESS", 20), audit("FAILED", 100), audit("SUCCESS", 30)));
        ToolCircuitBreakerRegistry circuit = new ToolCircuitBreakerRegistry();

        var result = new AdminToolDiagnosticsService(new GatewayToolRegistry(List.of(descriptor)), repository, circuit).list();

        assertThat(result).hasSize(1);
        var item = result.getFirst();
        assertThat(item.qualifiedName()).isEqualTo("local.searchKnowledge@1");
        assertThat(item.allowedAgents()).containsExactly("knowledge", "triage");
        assertThat(item.successRate()).isEqualTo(0.75);
        assertThat(item.p95DurationMs()).isEqualTo(100);
        assertThat(item.circuitState()).isEqualTo("CLOSED");
        assertThat(java.util.Arrays.stream(item.getClass().getRecordComponents()).map(component -> component.getName()).toList())
                .doesNotContain("argumentsSummary", "argumentsHash", "data");
    }

    private ToolAuditEntity audit(String status, long durationMs) {
        ToolAuditEntity entity = new ToolAuditEntity();
        ReflectionTestUtils.setField(entity, "status", status);
        ReflectionTestUtils.setField(entity, "durationMs", durationMs);
        return entity;
    }
}
