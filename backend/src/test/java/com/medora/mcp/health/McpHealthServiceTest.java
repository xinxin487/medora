package com.medora.mcp.health;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class McpHealthServiceTest {
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
