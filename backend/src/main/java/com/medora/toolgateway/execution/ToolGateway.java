package com.medora.toolgateway.execution;
import com.medora.toolgateway.policy.*;
import com.medora.toolgateway.registry.GatewayToolRegistry;
import com.medora.toolgateway.schema.ToolSchemaValidator;
import java.util.Map;
import java.util.function.Function;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
@Component public class ToolGateway {
    private final GatewayToolRegistry registry; private final ToolAuthorizationPolicy authorization; private final ToolConfirmationPolicy confirmation; private final Map<String, Function<GatewayToolRequest, Object>> adapters; private final ToolSchemaValidator validator;
    @Autowired
    public ToolGateway(GatewayToolRegistry registry, ToolAuthorizationPolicy authorization, ToolConfirmationPolicy confirmation, ToolSchemaValidator validator) { this(registry, authorization, confirmation, validator, Map.of()); }
    public ToolGateway(GatewayToolRegistry registry, ToolAuthorizationPolicy authorization, ToolConfirmationPolicy confirmation, Map<String, Function<GatewayToolRequest, Object>> adapters) { this(registry, authorization, confirmation, new ToolSchemaValidator(new com.fasterxml.jackson.databind.ObjectMapper()), adapters); }
    public ToolGateway(GatewayToolRegistry registry, ToolAuthorizationPolicy authorization, ToolConfirmationPolicy confirmation, ToolSchemaValidator validator, Map<String, Function<GatewayToolRequest, Object>> adapters) { this.registry = registry; this.authorization = authorization; this.confirmation = confirmation; this.validator = validator; this.adapters = Map.copyOf(adapters); }
    public GatewayToolResult execute(GatewayToolRequest request) {
        var descriptor = registry.find(request.qualifiedToolName()).orElse(null);
        if (descriptor == null) return GatewayToolResult.failure(GatewayToolResult.Status.FAILED, GatewayErrorCode.TOOL_NOT_FOUND, "tool not found", false);
        if (!authorization.permits(descriptor, request.context())) return GatewayToolResult.failure(GatewayToolResult.Status.DENIED, GatewayErrorCode.DENIED, "tool access denied", false);
        if (!validator.validate(descriptor.inputSchema(), request.arguments()).isEmpty()) return GatewayToolResult.failure(GatewayToolResult.Status.FAILED, GatewayErrorCode.VALIDATION_FAILED, "tool arguments invalid", false);
        if (!confirmation.confirmed(descriptor, request.context())) return GatewayToolResult.failure(GatewayToolResult.Status.CONFIRMATION_REQUIRED, GatewayErrorCode.CONFIRMATION_REQUIRED, "confirmation required", false);
        var adapter = adapters.get(descriptor.qualifiedName());
        if (adapter == null) return GatewayToolResult.failure(GatewayToolResult.Status.UNAVAILABLE, GatewayErrorCode.ADAPTER_NOT_FOUND, "tool adapter unavailable", true);
        try { return GatewayToolResult.success(adapter.apply(request)); } catch (RuntimeException ex) { return GatewayToolResult.failure(GatewayToolResult.Status.FAILED, GatewayErrorCode.FAILED, "tool execution failed", descriptor.idempotent()); }
    }
}
