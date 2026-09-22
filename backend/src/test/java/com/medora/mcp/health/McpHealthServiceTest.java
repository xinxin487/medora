package com.medora.mcp.health;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class McpHealthServiceTest {
    @Test void tracksRegisteredHealthyAndDegradedStatesWithoutRawErrors() {
        McpHealthService health = new McpHealthService();

        health.markRegistered("directory");
        assertThat(health.get("directory").state()).isEqualTo(McpServerHealth.State.REGISTERED);
        health.markHealthy("directory");
        assertThat(health.get("directory").state()).isEqualTo(McpServerHealth.State.HEALTHY);
        assertThat(health.get("directory").lastSuccessAt()).isNotNull();
        health.markDegraded("directory", new IllegalStateException("Bearer secret-token"));

        McpServerHealth snapshot = health.get("directory");
        assertThat(snapshot.state()).isEqualTo(McpServerHealth.State.DEGRADED);
        assertThat(snapshot.lastError()).isEqualTo("MCP remote unavailable");
        assertThat(snapshot.lastError()).doesNotContain("secret-token");
    }

    @Test void recordsSanitizedAvailabilityPerRemoteServer() {
        McpHealthService health = new McpHealthService();
        health.markHealthy("directory");
        health.markUnavailable("directory", new IllegalStateException("Bearer secret-token"));

        McpServerHealth snapshot = health.get("directory");
        assertThat(snapshot.state()).isEqualTo(McpServerHealth.State.UNAVAILABLE);
        assertThat(snapshot.lastError()).isEqualTo("MCP remote unavailable");
        assertThat(snapshot.lastError()).doesNotContain("secret-token");
    }
}
