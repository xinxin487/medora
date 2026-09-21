package com.medora.toolgateway.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medora.toolgateway.descriptor.ToolDescriptor;
import com.medora.toolgateway.execution.GatewayToolRequest;
import com.medora.toolgateway.execution.GatewayToolResult;
import org.springframework.stereotype.Service;

@Service
public class ToolAuditService implements ToolAuditRecorder {

    private final ToolAuditRepository repository;
    private final ToolAuditSanitizer sanitizer;
    private final ObjectMapper objectMapper;

    public ToolAuditService(ToolAuditRepository repository, ToolAuditSanitizer sanitizer) {
        this.repository = repository;
        this.sanitizer = sanitizer;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public void record(ToolDescriptor descriptor, GatewayToolRequest request,
                       GatewayToolResult result, long durationMs) {
        try {
            var arguments = sanitizer.sanitize(request.arguments());
            ToolAuditEntity entity = ToolAuditEntity.of(descriptor, request, result, durationMs, arguments);
            entity.setArgumentsSummary(objectMapper.writeValueAsString(arguments.summary()));
            repository.save(entity);
        } catch (RuntimeException | JsonProcessingException ignored) {
            // Audit persistence is observational and must never fail a tool invocation.
        }
    }
}
