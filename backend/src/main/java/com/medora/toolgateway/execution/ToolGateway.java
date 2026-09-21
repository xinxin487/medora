package com.medora.toolgateway.execution;
import com.medora.toolgateway.policy.*;
import com.medora.toolgateway.registry.GatewayToolRegistry;
import com.medora.toolgateway.schema.ToolSchemaValidator;
import com.medora.toolgateway.adapter.ToolAdapter;
import com.medora.toolgateway.audit.ToolAuditRecorder;
import java.util.Map;
import java.util.function.Function;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
@Component public class ToolGateway {
    private final GatewayToolRegistry registry; private final ToolAuthorizationPolicy authorization; private final ToolConfirmationPolicy confirmation; private final Map<String, Function<GatewayToolRequest, Object>> adapters; private final java.util.List<ToolAdapter> adapterBeans; private final ToolSchemaValidator validator; private final ToolIsolationRegistry isolation; private final ToolAuditRecorder audit;
    @Autowired
    public ToolGateway(GatewayToolRegistry registry, ToolAuthorizationPolicy authorization, ToolConfirmationPolicy confirmation, ToolSchemaValidator validator, java.util.List<ToolAdapter> adapterBeans, ToolIsolationRegistry isolation, ToolAuditRecorder audit) { this(registry, authorization, confirmation, validator, Map.of(), adapterBeans, isolation, audit); }
    public ToolGateway(GatewayToolRegistry registry, ToolAuthorizationPolicy authorization, ToolConfirmationPolicy confirmation, Map<String, Function<GatewayToolRequest, Object>> adapters) { this(registry, authorization, confirmation, new ToolSchemaValidator(new com.fasterxml.jackson.databind.ObjectMapper()), adapters, java.util.List.of(), new ToolIsolationRegistry(), ToolAuditRecorder.noop()); }
    public ToolGateway(GatewayToolRegistry registry, ToolAuthorizationPolicy authorization, ToolConfirmationPolicy confirmation, Map<String, Function<GatewayToolRequest, Object>> adapters, ToolAuditRecorder audit) { this(registry, authorization, confirmation, new ToolSchemaValidator(new com.fasterxml.jackson.databind.ObjectMapper()), adapters, java.util.List.of(), new ToolIsolationRegistry(), audit); }
    public ToolGateway(GatewayToolRegistry registry, ToolAuthorizationPolicy authorization, ToolConfirmationPolicy confirmation, ToolSchemaValidator validator, Map<String, Function<GatewayToolRequest, Object>> adapters) { this(registry, authorization, confirmation, validator, adapters, java.util.List.of(), new ToolIsolationRegistry(), ToolAuditRecorder.noop()); }
    private ToolGateway(GatewayToolRegistry registry, ToolAuthorizationPolicy authorization, ToolConfirmationPolicy confirmation, ToolSchemaValidator validator, Map<String, Function<GatewayToolRequest, Object>> adapters, java.util.List<ToolAdapter> adapterBeans, ToolIsolationRegistry isolation, ToolAuditRecorder audit) { this.registry = registry; this.authorization = authorization; this.confirmation = confirmation; this.validator = validator; this.adapters = Map.copyOf(adapters); this.adapterBeans = java.util.List.copyOf(adapterBeans); this.isolation = isolation; this.audit = audit; }
    public GatewayToolResult execute(GatewayToolRequest request) {
        long started = System.nanoTime();
        var descriptor = registry.find(request.qualifiedToolName()).orElse(null);
        if (descriptor == null) return GatewayToolResult.failure(GatewayToolResult.Status.FAILED, GatewayErrorCode.TOOL_NOT_FOUND, "tool not found", false);
        if (!authorization.permits(descriptor, request.context())) return finish(descriptor, request, GatewayToolResult.failure(GatewayToolResult.Status.DENIED, GatewayErrorCode.DENIED, "tool access denied", false), started);
        if (!validator.validate(descriptor.inputSchema(), request.arguments()).isEmpty()) return finish(descriptor, request, GatewayToolResult.failure(GatewayToolResult.Status.FAILED, GatewayErrorCode.VALIDATION_FAILED, "tool arguments invalid", false), started);
        if (!confirmation.confirmed(descriptor, request.context())) return finish(descriptor, request, GatewayToolResult.failure(GatewayToolResult.Status.CONFIRMATION_REQUIRED, GatewayErrorCode.CONFIRMATION_REQUIRED, "confirmation required", false), started);
        ToolIsolationRegistry.Lease lease = isolation.tryAcquire(descriptor.qualifiedName());
        if (lease == null) return finish(descriptor, request, GatewayToolResult.failure(GatewayToolResult.Status.RATE_LIMITED, GatewayErrorCode.RATE_LIMITED, "tool bulkhead full", true), started);
        var adapter = adapters.get(descriptor.qualifiedName());
        java.util.concurrent.Callable<Object> call;
        if (adapter != null) call = () -> adapter.apply(request);
        else {
            var bean = adapterBeans.stream().filter(candidate -> candidate.supports(descriptor.qualifiedName())).findFirst().orElse(null);
            if (bean == null) { lease.close(); return finish(descriptor, request, GatewayToolResult.failure(GatewayToolResult.Status.UNAVAILABLE, GatewayErrorCode.ADAPTER_NOT_FOUND, "tool adapter unavailable", true), started); }
            call = () -> bean.execute(request);
        }
        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor();
        java.util.concurrent.Future<Object> future = executor.submit(call);
        try {
            Object data = future.get(descriptor.timeout().toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
            return finish(descriptor, request, GatewayToolResult.success(data), started);
        } catch (java.util.concurrent.TimeoutException ex) { future.cancel(true); return finish(descriptor, request, GatewayToolResult.failure(GatewayToolResult.Status.TIMEOUT, GatewayErrorCode.DEPENDENCY_TIMEOUT, "tool execution timed out", descriptor.idempotent()), started);
        } catch (Exception ex) { return finish(descriptor, request, GatewayToolResult.failure(GatewayToolResult.Status.FAILED, GatewayErrorCode.FAILED, "tool execution failed", descriptor.idempotent()), started);
        } finally { executor.shutdownNow(); lease.close(); }
    }
    private GatewayToolResult finish(com.medora.toolgateway.descriptor.ToolDescriptor descriptor, GatewayToolRequest request, GatewayToolResult result, long started) { try { audit.record(descriptor, request, result, Math.max(0, (System.nanoTime() - started) / 1_000_000)); } catch (RuntimeException ignored) { } return result; }
}
