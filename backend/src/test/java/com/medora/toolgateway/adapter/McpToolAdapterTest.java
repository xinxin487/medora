package com.medora.toolgateway.adapter;

import com.medora.mcp.client.McpServerConnection;
import com.medora.mcp.client.McpConnectionRegistry;
import com.medora.mcp.health.McpHealthService;
import com.medora.mcp.health.McpServerHealth;
import com.medora.toolgateway.execution.GatewayExecutionContext;
import com.medora.toolgateway.execution.GatewayToolRequest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class McpToolAdapterTest {
    @Test
    void callsTheNamespacedRemoteTool() {
        McpToolAdapter adapter = new McpToolAdapter(List.of(connection((name, arguments) -> Map.of("departmentCode", "RESP"))));

        Object result = adapter.execute(request("mcp.hospitalDirectory.findDepartment@1"));

        assertThat(result).isEqualTo(Map.of("departmentCode", "RESP"));
    }

    @Test
    void replacesRemoteErrorDetailsWithASafeMessage() {
        McpToolAdapter adapter = new McpToolAdapter(List.of(connection((name, arguments) -> {
            throw new IllegalStateException("Bearer secret-token from remote body");
        })));

        assertThatThrownBy(() -> adapter.execute(request("mcp.hospitalDirectory.findDepartment@1")))
                .isInstanceOf(McpToolAdapter.RemoteCallException.class)
                .hasMessage("MCP remote tool unavailable")
                .hasNoCause();
    }

    @Test
    void marksOnlyTheFailingRemoteAsUnavailable() {
        McpConnectionRegistry connections = new McpConnectionRegistry();
        connections.register(connection((name, arguments) -> { throw new IllegalStateException("remote unreachable"); }));
        McpHealthService health = new McpHealthService();
        health.markHealthy("hospitalDirectory");
        McpToolAdapter adapter = new McpToolAdapter(connections, health);

        assertThatThrownBy(() -> adapter.execute(request("mcp.hospitalDirectory.findDepartment@1")))
                .isInstanceOf(McpToolAdapter.RemoteCallException.class);
        assertThat(health.get("hospitalDirectory").state()).isEqualTo(McpServerHealth.State.UNAVAILABLE);
    }

    private GatewayToolRequest request(String toolName) {
        return new GatewayToolRequest(toolName, Map.of("symptoms", "cough"),
                new GatewayExecutionContext(1L, List.of("USER"), "triage", "conversation", "trace", "request", null));
    }

    private McpServerConnection connection(McpServerConnection.Call call) {
        return new McpServerConnection() {
            @Override public String serverName() { return "hospitalDirectory"; }
            @Override public List<com.medora.mcp.client.RemoteMcpTool> listTools() { return List.of(); }
            @Override public Object callTool(String name, Map<String, Object> arguments) { return call.call(name, arguments); }
        };
    }
}
