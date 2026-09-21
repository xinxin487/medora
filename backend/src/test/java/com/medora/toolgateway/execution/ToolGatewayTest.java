package com.medora.toolgateway.execution;

import com.medora.toolgateway.descriptor.*;
import com.medora.toolgateway.policy.ToolAuthorizationPolicy;
import com.medora.toolgateway.policy.ToolConfirmationPolicy;
import com.medora.toolgateway.registry.GatewayToolRegistry;
import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.assertj.core.api.Assertions.assertThat;

class ToolGatewayTest {
    @Test void deniesUnauthorizedRequestBeforeAdapterRuns() {
        ToolDescriptor descriptor = descriptor();
        AtomicBoolean called = new AtomicBoolean();
        ToolGateway gateway = new ToolGateway(new GatewayToolRegistry(List.of(descriptor)), new ToolAuthorizationPolicy(), new ToolConfirmationPolicy(),
                Map.of(descriptor.qualifiedName(), (request) -> { called.set(true); return Map.of("ok", true); }));
        GatewayToolResult result = gateway.execute(new GatewayToolRequest(descriptor.qualifiedName(), Map.of(), GatewayExecutionContext.anonymous()));
        assertThat(result.status()).isEqualTo(GatewayToolResult.Status.DENIED);
        assertThat(called).isFalse();
    }
    @Test void requiresConfirmationBeforeHighRiskAdapterRuns() {
        ToolDescriptor descriptor = new ToolDescriptor("local.bookAppointment", "1", "", ToolSourceType.LOCAL, Map.of(), Map.of(), ToolRisk.HIGH, List.of("USER"), List.of("booking"), Duration.ofSeconds(1), false, true, "INTERNAL");
        ToolGateway gateway = new ToolGateway(new GatewayToolRegistry(List.of(descriptor)), new ToolAuthorizationPolicy(), new ToolConfirmationPolicy(), Map.of());
        var context = new GatewayExecutionContext(1L, List.of("USER"), "booking", "c", "t", "r", null);
        assertThat(gateway.execute(new GatewayToolRequest(descriptor.qualifiedName(), Map.of(), context)).status()).isEqualTo(GatewayToolResult.Status.CONFIRMATION_REQUIRED);
    }
    @Test void rejectsInvalidArgumentsBeforeAdapterRuns() {
        ToolDescriptor descriptor = new ToolDescriptor("local.queryScheduleSlots", "1", "", ToolSourceType.LOCAL,
                Map.of("type", "object", "additionalProperties", false, "properties", Map.of("pageSize", Map.of("type", "integer", "maximum", 5))), Map.of(), ToolRisk.LOW, List.of("USER"), List.of("booking"), Duration.ofSeconds(1), true, false, "INTERNAL");
        AtomicBoolean called = new AtomicBoolean();
        ToolGateway gateway = new ToolGateway(new GatewayToolRegistry(List.of(descriptor)), new ToolAuthorizationPolicy(), new ToolConfirmationPolicy(), Map.of(descriptor.qualifiedName(), request -> { called.set(true); return Map.of(); }));
        var context = new GatewayExecutionContext(1L, List.of("USER"), "booking", "c", "t", "r", null);
        assertThat(gateway.execute(new GatewayToolRequest(descriptor.qualifiedName(), Map.of("surprise", true), context)).status()).isEqualTo(GatewayToolResult.Status.FAILED);
        assertThat(called).isFalse();
    }
    private ToolDescriptor descriptor() { return new ToolDescriptor("local.queryScheduleSlots", "1", "", ToolSourceType.LOCAL, Map.of(), Map.of(), ToolRisk.LOW, List.of("USER"), List.of("booking"), Duration.ofSeconds(1), true, false, "INTERNAL"); }
}
