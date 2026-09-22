package com.medora.toolgateway.adapter;

import com.medora.mcp.client.McpServerConnection;
import com.medora.toolgateway.execution.GatewayToolRequest;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class McpToolAdapter implements ToolAdapter {
    private final List<McpServerConnection> connections;

    public McpToolAdapter(List<McpServerConnection> connections) {
        this.connections = List.copyOf(connections);
    }

    @Override
    public boolean supports(String qualifiedToolName) {
        return qualifiedToolName != null && qualifiedToolName.matches("mcp\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+@1");
    }

    @Override
    public Object execute(GatewayToolRequest request) {
        String[] parts = request.qualifiedToolName().substring(4, request.qualifiedToolName().length() - 2).split("\\.", 2);
        McpServerConnection connection = connections.stream()
                .filter(candidate -> candidate.serverName().equals(parts[0]))
                .findFirst()
                .orElseThrow(() -> new RemoteCallException());
        try {
            return connection.callTool(parts[1], request.arguments());
        } catch (RuntimeException exception) {
            throw new RemoteCallException();
        }
    }

    public static class RemoteCallException extends RuntimeException {
        public RemoteCallException() {
            super("MCP remote tool unavailable", null, false, false);
        }
    }
}
