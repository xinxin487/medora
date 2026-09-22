package com.medora.mcp.client;

import com.medora.toolgateway.descriptor.ToolDescriptor;
import com.medora.toolgateway.descriptor.ToolRisk;
import com.medora.toolgateway.descriptor.ToolSourceType;
import com.medora.toolgateway.registry.GatewayToolRegistry;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Set;

@Service
public class McpDiscoveryService {
    private final GatewayToolRegistry registry;

    public McpDiscoveryService(GatewayToolRegistry registry) {
        this.registry = registry;
    }

    public List<ToolDescriptor> registerAllowedTools(String serverName, List<String> allowedTools,
                                                      List<RemoteMcpTool> discoveredTools) {
        validateName(serverName, "server name");
        Set<String> allowed = Set.copyOf(allowedTools);
        return discoveredTools.stream()
                .filter(tool -> allowed.contains(tool.name()))
                .map(tool -> descriptor(serverName, tool))
                .peek(registry::registerRemote)
                .toList();
    }

    private ToolDescriptor descriptor(String serverName, RemoteMcpTool tool) {
        validateName(tool.name(), "tool name");
        return new ToolDescriptor("mcp." + serverName + "." + tool.name(), "1", tool.description(),
                ToolSourceType.MCP, tool.inputSchema(), tool.outputSchema(), ToolRisk.LOW,
                List.of("USER"), List.of("booking", "triage", "knowledge"), Duration.ofSeconds(3),
                true, false, "INTERNAL");
    }

    private void validateName(String value, String label) {
        if (value == null || !value.matches("[A-Za-z0-9_-]+")) {
            throw new IllegalArgumentException("invalid MCP " + label);
        }
    }
}
