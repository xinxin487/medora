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
    private final GatewayToolRegistry registry; private final ToolAuthorizationPolicy authorization; private final ToolConfirmationPolicy confirmation; private final Map<String, Function<GatewayToolRequest, Object>> adapters; private final java.util.List<ToolAdapter> adapterBeans; private final ToolSchemaValidator validator; private final ToolIsolationRegistry isolation;
    @Autowired
    public ToolGateway(GatewayToolRegistry registry, ToolAuthorizationPolicy authorization, ToolConfirmationPolicy confirmation, ToolSchemaValidator validator, java.util.List<ToolAdapter> adapterBeans, ToolIsolationRegistry isolation) { this(registry, authorization, confirmation, validator, Map.of(), adapterBeans, isolation); }
    public ToolGateway(GatewayToolRegistry registry, ToolAuthorizationPolicy authorization, ToolConfirmationPolicy confirmation, Map<String, Function<GatewayToolRequest, Object>> adapters) { this(registry, authorization, confirmation, new ToolSchemaValidator(new com.fasterxml.jackson.databind.ObjectMapper()), adapters, java.util.List.of(), new ToolIsolationRegistry()); }
    public ToolGateway(GatewayToolRegistry registry, ToolAuthorizationPolicy authorization, ToolConfirmationPolicy confirmation, ToolSchemaValidator validator, Map<String, Function<GatewayToolRequest, Object>> adapters) { this(registry, authorization, confirmation, validator, adapters, java.util.List.of(), new ToolIsolationRegistry()); }
    private ToolGateway(GatewayToolRegistry registry, ToolAuthorizationPolicy authorization, ToolConfirmationPolicy confirmation, ToolSchemaValidator validator, Map<String, Function<GatewayToolRequest, Object>> adapters, java.util.List<ToolAdapter> adapterBeans, ToolIsolationRegistry isolation) { this.registry = registry; this.authorization = authorization; this.confirmation = confirmation; this.validator = validator; this.adapters = Map.copyOf(adapters); this.adapterBeans = java.util.List.copyOf(adapterBeans); this.isolation = isolation; }
    public GatewayToolResult execute(GatewayToolRequest request) {
        var descriptor = registry.find(request.qualifiedToolName()).orElse(null);
        if (descriptor == null) return GatewayToolResult.failure(GatewayToolResult.Status.FAILED, GatewayErrorCode.TOOL_NOT_FOUND, "tool not found", false);
        if (!authorization.permits(descriptor, request.context())) return GatewayToolResult.failure(GatewayToolResult.Status.DENIED, GatewayErrorCode.DENIED, "tool access denied", false);
        if (!validator.validate(descriptor.inputSchema(), request.arguments()).isEmpty()) return GatewayToolResult.failure(GatewayToolResult.Status.FAILED, GatewayErrorCode.VALIDATION_FAILED, "tool arguments invalid", false);
        if (!confirmation.confirmed(descriptor, request.context())) return GatewayToolResult.failure(GatewayToolResult.Status.CONFIRMATION_REQUIRED, GatewayErrorCode.CONFIRMATION_REQUIRED, "confirmation required", false);
        ToolIsolationRegistry.Lease lease = isolation.tryAcquire(descriptor.qualifiedName());
        if (lease == null) return GatewayToolResult.failure(GatewayToolResult.Status.RATE_LIMITED, GatewayErrorCode.RATE_LIMITED, "tool bulkhead full", true);
        var adapter = adapters.get(descriptor.qualifiedName());
        java.util.concurrent.Callable<Object> call;
        if (adapter != null) call = () -> adapter.apply(request);
        else {
            var bean = adapterBeans.stream().filter(candidate -> candidate.supports(descriptor.qualifiedName())).findFirst().orElse(null);
            if (bean == null) return GatewayToolResult.failure(GatewayToolResult.Status.UNAVAILABLE, GatewayErrorCode.ADAPTER_NOT_FOUND, "tool adapter unavailable", true);
            call = () -> bean.execute(request);
        }
        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor();
        java.util.concurrent.Future<Object> future = executor.submit(call);
        try {
            Object data = future.get(descriptor.timeout().toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
            return GatewayToolResult.success(data);
        } catch (java.util.concurrent.TimeoutException ex) { future.cancel(true); return GatewayToolResult.failure(GatewayToolResult.Status.TIMEOUT, GatewayErrorCode.DEPENDENCY_TIMEOUT, "tool execution timed out", descriptor.idempotent());
        } catch (Exception ex) { return GatewayToolResult.failure(GatewayToolResult.Status.FAILED, GatewayErrorCode.FAILED, "tool execution failed", descriptor.idempotent());
        } finally { executor.shutdownNow(); lease.close(); }
    }
}
