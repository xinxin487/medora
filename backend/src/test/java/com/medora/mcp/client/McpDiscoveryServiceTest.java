package com.medora.mcp.client;

import com.medora.toolgateway.descriptor.ToolDescriptor;
import com.medora.toolgateway.descriptor.ToolRisk;
import com.medora.toolgateway.descriptor.ToolSourceType;
import com.medora.toolgateway.registry.GatewayToolRegistry;
import com.medora.mcp.config.McpProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class McpDiscoveryServiceTest {
    @Test
    void registersOnlyAllowListedRemoteToolsUnderTheMcpNamespace() {
        ToolDescriptor local = new ToolDescriptor("local.findDepartment", "1", "local", ToolSourceType.LOCAL,
                Map.of(), Map.of(), ToolRisk.LOW, List.of("USER"), List.of("booking"), Duration.ofSeconds(1), true, false, "INTERNAL");
        GatewayToolRegistry registry = new GatewayToolRegistry(List.of(local));
        McpDiscoveryService discovery = new McpDiscoveryService(registry);

        List<ToolDescriptor> registered = discovery.registerAllowedTools("hospitalDirectory", List.of("findDepartment"), List.of(
                new RemoteMcpTool("findDepartment", "department lookup", Map.of("type", "object"), Map.of("type", "object")),
                new RemoteMcpTool("deletePatient", "unsafe", Map.of("type", "object"), Map.of("type", "object"))));

        assertThat(registered).extracting(ToolDescriptor::qualifiedName).containsExactly("mcp.hospitalDirectory.findDepartment@1");
        assertThat(registry.find("mcp.hospitalDirectory.findDepartment@1")).isPresent();
        assertThat(registry.find("mcp.hospitalDirectory.deletePatient@1")).isEmpty();
        assertThat(registry.find("local.findDepartment@1")).contains(local);
    }

    @Test
    void discoversConfiguredRemoteToolsWithoutFailingWhenAnotherRemoteIsUnavailable() {
        GatewayToolRegistry gateway = new GatewayToolRegistry(List.of());
        McpConnectionRegistry connections = new McpConnectionRegistry();
        McpProperties properties = new McpProperties();
        properties.setEnabled(true); properties.setServerToken("server-token"); properties.setAllowedOrigins(List.of("https://medora.example"));
        McpProperties.RemoteServer available = remote("directory", "DIRECTORY_TOKEN");
        McpProperties.RemoteServer unavailable = remote("unavailable", "UNAVAILABLE_TOKEN");
        properties.setRemoteServers(List.of(available, unavailable));
        McpClientFactory factory = new McpClientFactory(new ObjectMapper()) {
            @Override public McpServerConnection connect(McpProperties.RemoteServer remote) {
                if (remote.getName().equals("unavailable")) throw new McpConnectionException("MCP remote unavailable");
                return new McpServerConnection() {
                    @Override public String serverName() { return "directory"; }
                    @Override public List<RemoteMcpTool> listTools() { return List.of(new RemoteMcpTool("findDepartment", "lookup", Map.of(), Map.of())); }
                    @Override public Object callTool(String name, Map<String, Object> arguments) { return Map.of(); }
                };
            }
        };
        McpDiscoveryService discovery = new McpDiscoveryService(gateway, properties, factory, connections);

        discovery.discoverConfiguredTools();

        assertThat(gateway.find("mcp.directory.findDepartment@1")).isPresent();
        assertThat(connections.find("directory")).isPresent();
        assertThat(connections.find("unavailable")).isEmpty();
    }

    private McpProperties.RemoteServer remote(String name, String tokenEnvName) {
        McpProperties.RemoteServer remote = new McpProperties.RemoteServer();
        remote.setName(name); remote.setBaseUrl("https://" + name + ".example/mcp"); remote.setTokenEnvName(tokenEnvName); remote.setAllowedTools(List.of("findDepartment"));
        return remote;
    }
}
