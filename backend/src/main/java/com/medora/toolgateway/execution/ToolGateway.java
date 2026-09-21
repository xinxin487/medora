package com.medora.toolgateway.execution;
import com.medora.toolgateway.policy.*;
import com.medora.toolgateway.registry.GatewayToolRegistry;
import com.medora.toolgateway.schema.ToolSchemaValidator;
import com.medora.toolgateway.adapter.ToolAdapter;
import java.util.Map;
import java.util.function.Function;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
@Component public class ToolGateway {
    private final GatewayToolRegistry registry; private final ToolAuthorizationPolicy authorization; private final ToolConfirmationPolicy confirmation; private final Map<String, Function<GatewayToolRequest, Object>> adapters; private final java.util.List<ToolAdapter> adapterBeans; private final ToolSchemaValidator validator;
    @Autowired
    public ToolGateway(GatewayToolRegistry registry, ToolAuthorizationPolicy authorization, ToolConfirmationPolicy confirmation, ToolSchemaValidator validator, java.util.List<ToolAdapter> adapterBeans) { this(registry, authorization, confirmation, validator, Map.of(), adapterBeans); }
    public ToolGateway(GatewayToolRegistry registry, ToolAuthorizationPolicy authorization, ToolConfirmationPolicy confirmation, Map<String, Function<GatewayToolRequest, Object>> adapters) { this(registry, authorization, confirmation, new ToolSchemaValidator(new com.fasterxml.jackson.databind.ObjectMapper()), adapters, java.util.List.of()); }
    public ToolGateway(GatewayToolRegistry registry, ToolAuthorizationPolicy authorization, ToolConfirmationPolicy confirmation, ToolSchemaValidator validator, Map<String, Function<GatewayToolRequest, Object>> adapters) { this(registry, authorization, confirmation, validator, adapters, java.util.List.of()); }
    private ToolGateway(GatewayToolRegistry registry, ToolAuthorizationPolicy authorization, ToolConfirmationPolicy confirmation, ToolSchemaValidator validator, Map<String, Function<GatewayToolRequest, Object>> adapters, java.util.List<ToolAdapter> adapterBeans) { this.registry = registry; this.authorization = authorization; this.confirmation = confirmation; this.validator = validator; this.adapters = Map.copyOf(adapters); this.adapterBeans = java.util.List.copyOf(adapterBeans); }
    public GatewayToolResult execute(GatewayToolRequest request) {
        var descriptor = registry.find(request.qualifiedToolName()).orElse(null);
        if (descriptor == null) return GatewayToolResult.failure(GatewayToolResult.Status.FAILED, GatewayErrorCode.TOOL_NOT_FOUND, "tool not found", false);
        if (!authorization.permits(descriptor, request.context())) return GatewayToolResult.failure(GatewayToolResult.Status.DENIED, GatewayErrorCode.DENIED, "tool access denied", false);
        if (!validator.validate(descriptor.inputSchema(), request.arguments()).isEmpty()) return GatewayToolResult.failure(GatewayToolResult.Status.FAILED, GatewayErrorCode.VALIDATION_FAILED, "tool arguments invalid", false);
        if (!confirmation.confirmed(descriptor, request.context())) return GatewayToolResult.failure(GatewayToolResult.Status.CONFIRMATION_REQUIRED, GatewayErrorCode.CONFIRMATION_REQUIRED, "confirmation required", false);
        var adapter = adapters.get(descriptor.qualifiedName());
        try {
            if (adapter != null) return GatewayToolResult.success(adapter.apply(request));
            return adapterBeans.stream().filter(candidate -> candidate.supports(descriptor.qualifiedName())).findFirst()
                    .map(candidate -> GatewayToolResult.success(candidate.execute(request)))
                    .orElseGet(() -> GatewayToolResult.failure(GatewayToolResult.Status.UNAVAILABLE, GatewayErrorCode.ADAPTER_NOT_FOUND, "tool adapter unavailable", true));
        } catch (RuntimeException ex) { return GatewayToolResult.failure(GatewayToolResult.Status.FAILED, GatewayErrorCode.FAILED, "tool execution failed", descriptor.idempotent()); }
    }
}
