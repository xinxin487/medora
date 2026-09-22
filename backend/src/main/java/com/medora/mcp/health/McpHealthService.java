package com.medora.mcp.health;

import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

@Service public class McpHealthService {
    private final ConcurrentHashMap<String, McpServerHealth> states = new ConcurrentHashMap<>();
    public void markHealthy(String server) { states.compute(server, (key, previous) -> new McpServerHealth(key, McpServerHealth.State.HEALTHY, Instant.now(), previous == null ? null : previous.lastFailureAt(), null)); }
    public void markUnavailable(String server, Exception ignored) { states.compute(server, (key, previous) -> new McpServerHealth(key, McpServerHealth.State.UNAVAILABLE, previous == null ? null : previous.lastSuccessAt(), Instant.now(), "MCP remote unavailable")); }
    public McpServerHealth get(String server) { return states.getOrDefault(server, new McpServerHealth(server, McpServerHealth.State.DISABLED, null, null, null)); }
}
