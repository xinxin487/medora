package com.medora.admin.controller;

import com.medora.admin.service.AdminToolDiagnosticsService;
import com.medora.mcp.health.McpHealthService;
import com.medora.mcp.health.McpServerHealth;
import com.medora.mcp.health.McpRefreshService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class AdminToolControllerTest {
    @Test
    void exposesOnlySanitizedMcpHealthSnapshots() {
        McpHealthService health = new McpHealthService();
        health.markUnavailable("directory", new IllegalStateException("Bearer secret-token"));
        AdminToolController controller = new AdminToolController(mock(AdminToolDiagnosticsService.class),
                mock(McpRefreshService.class), health);

        List<McpServerHealth> snapshots = controller.mcpHealth().data();

        assertThat(snapshots).singleElement().satisfies(snapshot -> {
            assertThat(snapshot.serverName()).isEqualTo("directory");
            assertThat(snapshot.lastError()).isEqualTo("MCP remote unavailable");
            assertThat(snapshot.lastError()).doesNotContain("secret-token");
        });
    }
}
