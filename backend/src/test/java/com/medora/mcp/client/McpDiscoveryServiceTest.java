package com.medora.mcp.client;

import com.medora.toolgateway.descriptor.ToolDescriptor;
import com.medora.toolgateway.descriptor.ToolRisk;
import com.medora.toolgateway.descriptor.ToolSourceType;
import com.medora.toolgateway.registry.GatewayToolRegistry;
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
}
