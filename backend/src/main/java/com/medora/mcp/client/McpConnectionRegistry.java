package com.medora.mcp.client;

import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Mutable connection state is isolated from the immutable Gateway descriptor registry. */
@Component
public class McpConnectionRegistry {
    private final ConcurrentHashMap<String, McpServerConnection> connections = new ConcurrentHashMap<>();

    public void register(McpServerConnection connection) {
        connections.put(connection.serverName(), connection);
    }

    public Optional<McpServerConnection> find(String serverName) {
        return Optional.ofNullable(connections.get(serverName));
    }
}
