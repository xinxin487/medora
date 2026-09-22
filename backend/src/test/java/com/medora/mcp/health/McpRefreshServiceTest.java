package com.medora.mcp.health;

import com.medora.mcp.client.McpDiscoveryService;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class McpRefreshServiceTest {
    @Test void rateLimitsAdminRefreshRequests() {
        McpDiscoveryService discovery = mock(McpDiscoveryService.class);
        McpRefreshService refresh = new McpRefreshService(discovery);
        refresh.refresh();

        assertThatThrownBy(refresh::refresh).isInstanceOf(IllegalStateException.class).hasMessage("MCP refresh rate limited");
        verify(discovery).discoverConfiguredTools();
    }
}
