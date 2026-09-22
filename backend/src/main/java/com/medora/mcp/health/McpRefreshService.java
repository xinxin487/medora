package com.medora.mcp.health;

import com.medora.mcp.client.McpDiscoveryService;
import org.springframework.stereotype.Service;
import java.time.Duration;
import java.time.Instant;

@Service public class McpRefreshService {
    private final McpDiscoveryService discovery; private Instant lastRefresh = Instant.EPOCH;
    public McpRefreshService(McpDiscoveryService discovery) { this.discovery = discovery; }
    public synchronized void refresh() {
        if (lastRefresh.plus(Duration.ofSeconds(30)).isAfter(Instant.now())) throw new IllegalStateException("MCP refresh rate limited");
        lastRefresh = Instant.now(); discovery.discoverConfiguredTools();
    }
}
