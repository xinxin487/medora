package com.medora.toolgateway.adapter;

import com.medora.mcp.client.McpServerConnection;
import com.medora.mcp.client.McpConnectionRegistry;
import com.medora.mcp.health.McpHealthService;
import com.medora.toolgateway.execution.GatewayToolRequest;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class McpToolAdapter implements ToolAdapter {
    private final McpConnectionRegistry connections;
    private final McpHealthService health;

    @org.springframework.beans.factory.annotation.Autowired
    public McpToolAdapter(McpConnectionRegistry connections, McpHealthService health) {
        this.connections = connections;
        this.health = health;
    }
    public McpToolAdapter(McpConnectionRegistry connections) {
        this(connections, null);
    }
    public McpToolAdapter(List<McpServerConnection> connections) {
        this.connections = new McpConnectionRegistry();
        connections.forEach(this.connections::register);
        this.health = null;
    }

    @Override
    public boolean supports(String qualifiedToolName) {
        return qualifiedToolName != null && qualifiedToolName.matches("mcp\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+@1");
    }

    @Override
    public Object execute(GatewayToolRequest request) {
        String[] parts = request.qualifiedToolName().substring(4, request.qualifiedToolName().length() - 2).split("\\.", 2);
        McpServerConnection connection = connections.find(parts[0])
                .orElseThrow(() -> new RemoteCallException());
        try {
            Object result = connection.callTool(parts[1], request.arguments());
            if (health != null) health.markHealthy(parts[0]);
            return result;
        } catch (RuntimeException exception) {
            if (health != null) health.markUnavailable(parts[0], exception);
            throw new RemoteCallException();
        }
    }

    public static class RemoteCallException extends RuntimeException {
        public RemoteCallException() {
            super("MCP remote tool unavailable", null, false, false);
        }
    }
}
