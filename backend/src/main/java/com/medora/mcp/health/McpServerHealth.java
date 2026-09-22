package com.medora.mcp.health;

import java.time.Instant;

public record McpServerHealth(String serverName, State state, Instant lastSuccessAt, Instant lastFailureAt, String lastError) {
    public enum State { REGISTERED, HEALTHY, DEGRADED, UNAVAILABLE, DISABLED }
}
