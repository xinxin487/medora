package com.medora.mcp.client;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class McpConnectionRegistryTest {
    @Test
    void exposesConnectionsRegisteredAfterTheGatewayIsConstructed() {
        McpConnectionRegistry registry = new McpConnectionRegistry();
        McpServerConnection connection = new McpServerConnection() {
            @Override public String serverName() { return "directory"; }
            @Override public List<RemoteMcpTool> listTools() { return List.of(); }
            @Override public Object callTool(String name, Map<String, Object> arguments) { return Map.of(); }
        };

        registry.register(connection);

        assertThat(registry.find("directory")).contains(connection);
    }
}
