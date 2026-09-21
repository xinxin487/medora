package com.medora.toolgateway.execution;

import com.medora.toolgateway.descriptor.*;
import com.medora.toolgateway.policy.ToolAuthorizationPolicy;
import com.medora.toolgateway.policy.ToolConfirmationPolicy;
import com.medora.toolgateway.registry.GatewayToolRegistry;
import com.medora.toolgateway.audit.ToolAuditRecorder;
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
    @Test void returnsTimeoutWithoutWaitingForSlowAdapter() {
        ToolDescriptor descriptor = new ToolDescriptor("local.fast", "1", "", ToolSourceType.LOCAL, Map.of(), Map.of(), ToolRisk.LOW, List.of("USER"), List.of("booking"), Duration.ofMillis(20), true, false, "INTERNAL");
        ToolGateway gateway = new ToolGateway(new GatewayToolRegistry(List.of(descriptor)), new ToolAuthorizationPolicy(), new ToolConfirmationPolicy(), Map.of(descriptor.qualifiedName(), request -> { try { Thread.sleep(200); } catch (InterruptedException ignored) { } return Map.of(); }));
        long started = System.nanoTime();
        var context = new GatewayExecutionContext(1L, List.of("USER"), "booking", "c", "t", "r", null);
        assertThat(gateway.execute(new GatewayToolRequest(descriptor.qualifiedName(), Map.of(), context)).status()).isEqualTo(GatewayToolResult.Status.TIMEOUT);
        assertThat((System.nanoTime() - started) / 1_000_000).isLessThan(150);
    }
    @Test void recordsTheFinalGatewayOutcomeWithoutChangingItWhenAuditFails() {
        ToolDescriptor descriptor = descriptor();
        AtomicBoolean audited = new AtomicBoolean();
        ToolAuditRecorder failingAudit = (tool, request, result, durationMs) -> {
            audited.set(result.status() == GatewayToolResult.Status.SUCCESS && durationMs >= 0);
            throw new IllegalStateException("audit unavailable");
        };
        ToolGateway gateway = new ToolGateway(new GatewayToolRegistry(List.of(descriptor)), new ToolAuthorizationPolicy(), new ToolConfirmationPolicy(),
                Map.of(descriptor.qualifiedName(), request -> Map.of("ok", true)), failingAudit);
        var context = new GatewayExecutionContext(1L, List.of("USER"), "booking", "c", "t", "r", null);

        GatewayToolResult result = gateway.execute(new GatewayToolRequest(descriptor.qualifiedName(), Map.of(), context));

        assertThat(result.status()).isEqualTo(GatewayToolResult.Status.SUCCESS);
        assertThat(audited).isTrue();
    }
    @Test void rejectsAdapterOutputThatDoesNotMatchDescriptorSchema() {
        ToolDescriptor descriptor = new ToolDescriptor("local.checked", "1", "", ToolSourceType.LOCAL, Map.of(),
                Map.of("type", "object", "additionalProperties", false, "required", List.of("ok"),
                        "properties", Map.of("ok", Map.of("type", "boolean"))),
                ToolRisk.LOW, List.of("USER"), List.of("booking"), Duration.ofSeconds(1), true, false, "INTERNAL");
        ToolGateway gateway = new ToolGateway(new GatewayToolRegistry(List.of(descriptor)), new ToolAuthorizationPolicy(), new ToolConfirmationPolicy(),
                Map.of(descriptor.qualifiedName(), request -> Map.of("unexpected", true)));
        var context = new GatewayExecutionContext(1L, List.of("USER"), "booking", "c", "t", "r", null);

        GatewayToolResult result = gateway.execute(new GatewayToolRequest(descriptor.qualifiedName(), Map.of(), context));

        assertThat(result.status()).isEqualTo(GatewayToolResult.Status.FAILED);
        assertThat(result.errorCode()).isEqualTo(GatewayErrorCode.VALIDATION_FAILED);
    }
    @Test void retriesAnIdempotentToolOnceAfterTransientAdapterFailure() {
        ToolDescriptor descriptor = descriptor();
        java.util.concurrent.atomic.AtomicInteger attempts = new java.util.concurrent.atomic.AtomicInteger();
        ToolGateway gateway = new ToolGateway(new GatewayToolRegistry(List.of(descriptor)), new ToolAuthorizationPolicy(), new ToolConfirmationPolicy(),
                Map.of(descriptor.qualifiedName(), request -> {
                    if (attempts.incrementAndGet() == 1) throw new IllegalStateException("transient");
                    return Map.of("ok", true);
                }));
        var context = new GatewayExecutionContext(1L, List.of("USER"), "booking", "c", "t", "r", null);

        GatewayToolResult result = gateway.execute(new GatewayToolRequest(descriptor.qualifiedName(), Map.of(), context));

        assertThat(result.status()).isEqualTo(GatewayToolResult.Status.SUCCESS);
        assertThat(attempts).hasValue(2);
    }
    @Test void neverRetriesANonIdempotentToolAfterAdapterFailure() {
        ToolDescriptor descriptor = new ToolDescriptor("local.write", "1", "", ToolSourceType.LOCAL, Map.of(), Map.of(), ToolRisk.HIGH, List.of("USER"), List.of("booking"), Duration.ofSeconds(1), false, false, "INTERNAL");
        java.util.concurrent.atomic.AtomicInteger attempts = new java.util.concurrent.atomic.AtomicInteger();
        ToolGateway gateway = new ToolGateway(new GatewayToolRegistry(List.of(descriptor)), new ToolAuthorizationPolicy(), new ToolConfirmationPolicy(),
                Map.of(descriptor.qualifiedName(), request -> { attempts.incrementAndGet(); throw new IllegalStateException("transient"); }));
        var context = new GatewayExecutionContext(1L, List.of("USER"), "booking", "c", "t", "r", null);

        GatewayToolResult result = gateway.execute(new GatewayToolRequest(descriptor.qualifiedName(), Map.of(), context));

        assertThat(result.status()).isEqualTo(GatewayToolResult.Status.FAILED);
        assertThat(attempts).hasValue(1);
    }
    @Test void rejectsCallsWhileTheToolCircuitIsOpen() {
        ToolDescriptor descriptor = new ToolDescriptor("local.unstable", "1", "", ToolSourceType.LOCAL, Map.of(), Map.of(), ToolRisk.LOW, List.of("USER"), List.of("booking"), Duration.ofSeconds(1), false, false, "INTERNAL");
        ToolGateway gateway = new ToolGateway(new GatewayToolRegistry(List.of(descriptor)), new ToolAuthorizationPolicy(), new ToolConfirmationPolicy(),
                Map.of(descriptor.qualifiedName(), request -> { throw new IllegalStateException("down"); }));
        var context = new GatewayExecutionContext(1L, List.of("USER"), "booking", "c", "t", "r", null);
        for (int ignored = 0; ignored < 3; ignored++) gateway.execute(new GatewayToolRequest(descriptor.qualifiedName(), Map.of(), context));

        GatewayToolResult result = gateway.execute(new GatewayToolRequest(descriptor.qualifiedName(), Map.of(), context));

        assertThat(result.status()).isEqualTo(GatewayToolResult.Status.UNAVAILABLE);
        assertThat(result.errorCode()).isEqualTo(GatewayErrorCode.CIRCUIT_OPEN);
    }
    @Test void doesNotConsumeHalfOpenProbeForARequestMissingConfirmation() {
        ToolDescriptor descriptor = new ToolDescriptor("local.confirmed", "1", "", ToolSourceType.LOCAL, Map.of(), Map.of(), ToolRisk.HIGH, List.of("USER"), List.of("booking"), Duration.ofSeconds(1), false, true, "INTERNAL");
        ToolCircuitBreakerRegistry circuit = new ToolCircuitBreakerRegistry(1, Duration.ZERO, java.time.Clock.systemUTC());
        circuit.recordFailure(descriptor.qualifiedName());
        ToolGateway gateway = new ToolGateway(new GatewayToolRegistry(List.of(descriptor)), new ToolAuthorizationPolicy(), new ToolConfirmationPolicy(),
                Map.of(descriptor.qualifiedName(), request -> Map.of("ok", true)), circuit);
        var withoutConfirmation = new GatewayExecutionContext(1L, List.of("USER"), "booking", "c", "t", "r", null);
        var confirmed = withoutConfirmation.withConfirmationActionId("confirmed-1");

        assertThat(gateway.execute(new GatewayToolRequest(descriptor.qualifiedName(), Map.of(), withoutConfirmation)).status())
                .isEqualTo(GatewayToolResult.Status.CONFIRMATION_REQUIRED);
        assertThat(gateway.execute(new GatewayToolRequest(descriptor.qualifiedName(), Map.of(), confirmed)).status())
                .isEqualTo(GatewayToolResult.Status.SUCCESS);
    }
    private ToolDescriptor descriptor() { return new ToolDescriptor("local.queryScheduleSlots", "1", "", ToolSourceType.LOCAL, Map.of(), Map.of(), ToolRisk.LOW, List.of("USER"), List.of("booking"), Duration.ofSeconds(1), true, false, "INTERNAL"); }
}
