package com.medora.admin.service;

import com.medora.admin.api.response.AdminToolDiagnosticItem;
import com.medora.toolgateway.audit.ToolAuditEntity;
import com.medora.toolgateway.audit.ToolAuditRepository;
import com.medora.toolgateway.descriptor.ToolDescriptor;
import com.medora.toolgateway.execution.ToolCircuitBreakerRegistry;
import com.medora.toolgateway.execution.GatewayToolResult;
import com.medora.toolgateway.registry.GatewayToolRegistry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

@Service
public class AdminToolDiagnosticsService {

    private final GatewayToolRegistry registry;
    private final ToolAuditRepository auditRepository;
    private final ToolCircuitBreakerRegistry circuit;

    public AdminToolDiagnosticsService(GatewayToolRegistry registry, ToolAuditRepository auditRepository,
                                       ToolCircuitBreakerRegistry circuit) {
        this.registry = registry;
        this.auditRepository = auditRepository;
        this.circuit = circuit;
    }

    @Transactional(readOnly = true)
    public List<AdminToolDiagnosticItem> list() {
        return registry.all().stream().sorted(Comparator.comparing(ToolDescriptor::qualifiedName))
                .map(this::toItem).toList();
    }

    private AdminToolDiagnosticItem toItem(ToolDescriptor descriptor) {
        List<ToolAuditEntity> samples = auditRepository.findTop500ByToolNameOrderByCreatedAtDesc(descriptor.name());
        long successCount = samples.stream().filter(sample -> GatewayToolResult.Status.SUCCESS.name().equals(sample.getStatus())).count();
        double successRate = samples.isEmpty() ? 0 : (double) successCount / samples.size();
        List<Long> durations = samples.stream().map(ToolAuditEntity::getDurationMs).sorted().toList();
        long p95 = durations.isEmpty() ? 0 : durations.get((int) Math.ceil(durations.size() * 0.95) - 1);
        return new AdminToolDiagnosticItem(descriptor.qualifiedName(), descriptor.description(), descriptor.sourceType().name(),
                descriptor.riskLevel().name(), descriptor.allowedAgents(), descriptor.idempotent(), descriptor.requiresConfirmation(),
                circuit.state(descriptor.qualifiedName()).name(), samples.size(), successRate, p95,
                descriptor.inputSchema(), descriptor.outputSchema());
    }
}
