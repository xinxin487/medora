package com.medora.mcp.client;

import com.medora.toolgateway.descriptor.ToolDescriptor;
import com.medora.toolgateway.descriptor.ToolRisk;
import com.medora.toolgateway.descriptor.ToolSourceType;
import com.medora.toolgateway.registry.GatewayToolRegistry;
import com.medora.mcp.config.McpProperties;
import com.medora.mcp.health.McpHealthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Set;

@Service
public class McpDiscoveryService {
    private final GatewayToolRegistry registry;
    private final McpProperties properties;
    private final McpClientFactory factory;
    private final McpConnectionRegistry connections;
    private final McpHealthService health;

    public McpDiscoveryService(GatewayToolRegistry registry) {
        this(registry, null, null, null, null);
    }

    public McpDiscoveryService(GatewayToolRegistry registry, McpProperties properties, McpClientFactory factory,
                               McpConnectionRegistry connections) {
        this(registry, properties, factory, connections, null);
    }

    @Autowired
    public McpDiscoveryService(GatewayToolRegistry registry, McpProperties properties, McpClientFactory factory,
                               McpConnectionRegistry connections, McpHealthService health) {
        this.registry = registry;
        this.properties = properties;
        this.factory = factory;
        this.connections = connections;
        this.health = health;
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

    @EventListener(ApplicationReadyEvent.class)
    public void discoverOnStartup() {
        if (properties != null && properties.isEnabled()) discoverConfiguredTools();
    }

    /** A remote outage is deliberately isolated: local Gateway tools remain available. */
    public void discoverConfiguredTools() {
        if (properties == null || factory == null || connections == null) return;
        try { properties.validate(); }
        catch (RuntimeException exception) { return; }
        for (McpProperties.RemoteServer remote : properties.getRemoteServers()) {
            if (health != null) health.markRegistered(remote.getName());
            try {
                McpServerConnection connection = factory.connect(remote);
                List<RemoteMcpTool> tools = connection.listTools();
                registerAllowedTools(remote.getName(), remote.getAllowedTools(), tools);
                connections.register(connection);
                if (health != null) health.markHealthy(remote.getName());
            } catch (RuntimeException exception) {
                if (health != null) health.markUnavailable(remote.getName(), exception);
                // Health state is recorded by the MCP health service; no remote detail reaches startup logs.
            }
        }
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
