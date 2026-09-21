package com.medora.toolgateway.policy;

import com.medora.toolgateway.descriptor.*;
import com.medora.toolgateway.execution.GatewayExecutionContext;
import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

class ToolPolicyTest {
    private final ToolDescriptor bookingRead = new ToolDescriptor("local.queryScheduleSlots", "1", "", ToolSourceType.LOCAL, Map.of(), Map.of(), ToolRisk.LOW, List.of("USER"), List.of("booking"), Duration.ofSeconds(1), true, false, "INTERNAL");
    @Test void permitsAllowedRoleAndAgentButDeniesMissingOrWrongContext() {
        ToolAuthorizationPolicy policy = new ToolAuthorizationPolicy();
        assertThat(policy.permits(bookingRead, context("booking", List.of("USER")))).isTrue();
        assertThat(policy.permits(bookingRead, context("knowledge", List.of("USER")))).isFalse();
        assertThat(policy.permits(bookingRead, context("booking", List.of("ADMIN")))).isFalse();
        assertThat(policy.permits(bookingRead, GatewayExecutionContext.anonymous())).isFalse();
    }
    @Test void requiresVerifiedConfirmationForHighRiskTool() {
        ToolDescriptor write = new ToolDescriptor("local.bookAppointment", "1", "", ToolSourceType.LOCAL, Map.of(), Map.of(), ToolRisk.HIGH, List.of("USER"), List.of("booking"), Duration.ofSeconds(1), false, true, "INTERNAL");
        assertThat(new ToolConfirmationPolicy().confirmed(write, context("booking", List.of("USER")))).isFalse();
        assertThat(new ToolConfirmationPolicy().confirmed(write, context("booking", List.of("USER")).withConfirmationActionId("confirmed-1"))).isTrue();
    }
    private GatewayExecutionContext context(String agent, List<String> roles) { return new GatewayExecutionContext(1L, roles, agent, "c", "t", "r", null); }
}
