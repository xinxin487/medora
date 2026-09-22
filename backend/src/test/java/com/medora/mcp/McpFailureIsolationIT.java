package com.medora.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medora.mcp.client.McpConnectionRegistry;
import com.medora.mcp.client.McpClientFactory;
import com.medora.mcp.client.McpDiscoveryService;
import com.medora.mcp.client.McpServerConnection;
import com.medora.mcp.client.RemoteMcpTool;
import com.medora.mcp.config.McpProperties;
import com.medora.mcp.health.McpHealthService;
import com.medora.mcp.health.McpRefreshService;
import com.medora.mcp.health.McpServerHealth;
import com.medora.toolgateway.adapter.McpToolAdapter;
import com.medora.toolgateway.descriptor.ToolDescriptor;
import com.medora.toolgateway.descriptor.ToolRisk;
import com.medora.toolgateway.descriptor.ToolSourceType;
import com.medora.toolgateway.execution.GatewayExecutionContext;
import com.medora.toolgateway.execution.GatewayToolRequest;
import com.medora.toolgateway.execution.GatewayToolResult;
import com.medora.toolgateway.execution.ToolGateway;
import com.medora.toolgateway.policy.ToolAuthorizationPolicy;
import com.medora.toolgateway.policy.ToolConfirmationPolicy;
import com.medora.toolgateway.registry.GatewayToolRegistry;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.json.jackson2.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.HttpServletStreamableServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class McpFailureIsolationIT {
    private ConfigurableApplicationContext server;
    private McpSyncClient client;

    @AfterEach
    void stopFixture() {
        if (client != null) client.close();
        if (server != null) server.close();
    }

    @Test
    void remoteOutageLeavesLocalToolAvailableAndRefreshRestoresTheRemote() {
        GatewayToolRegistry registry = new GatewayToolRegistry(List.of(localDescriptor()));
        McpConnectionRegistry connections = new McpConnectionRegistry();
        McpHealthService health = new McpHealthService();
        McpToolAdapter remoteAdapter = new McpToolAdapter(connections, health);
        ToolGateway gateway = gateway(registry, remoteAdapter);
        McpDiscoveryService discovery = discovery(registry, connections, health);
        McpRefreshService refresh = new McpRefreshService(discovery);

        startFixture();
        discovery.discoverConfiguredTools();
        assertThat(registry.find("mcp.directory.findDepartment@1")).isPresent();
        assertThat(gateway.execute(remoteRequest()).status()).isEqualTo(GatewayToolResult.Status.SUCCESS);
        assertThat(health.get("directory").state()).isEqualTo(McpServerHealth.State.HEALTHY);

        server.close();
        server = null;

        assertThat(gateway.execute(remoteRequest()).status()).isEqualTo(GatewayToolResult.Status.UNAVAILABLE);
        assertThat(health.get("directory").state()).isEqualTo(McpServerHealth.State.UNAVAILABLE);
        assertThat(gateway.execute(localRequest())).isEqualTo(GatewayToolResult.success(Map.of("items", List.of("local knowledge"))));

        client.close();
        client = null;
        startFixture();
        refresh.refresh();
        assertThat(gateway.execute(remoteRequest()).status()).isEqualTo(GatewayToolResult.Status.SUCCESS);
        assertThat(health.get("directory").state()).isEqualTo(McpServerHealth.State.HEALTHY);
    }

    private ToolGateway gateway(GatewayToolRegistry registry, McpToolAdapter remoteAdapter) {
        return new ToolGateway(registry, new ToolAuthorizationPolicy(), new ToolConfirmationPolicy(), Map.of(
                "local.searchKnowledge@1", request -> Map.of("items", List.of("local knowledge")),
                "mcp.directory.findDepartment@1", remoteAdapter::execute));
    }

    private ToolDescriptor localDescriptor() { return descriptor("local.searchKnowledge", List.of("knowledge")); }

    private McpDiscoveryService discovery(GatewayToolRegistry registry, McpConnectionRegistry connections, McpHealthService health) {
        McpProperties properties = new McpProperties();
        properties.setEnabled(true);
        properties.setServerToken("test-token");
        properties.setAllowedOrigins(List.of("https://test.example"));
        properties.setAllowedRemoteHosts(List.of("directory.example"));
        McpProperties.RemoteServer remote = new McpProperties.RemoteServer();
        remote.setName("directory");
        remote.setBaseUrl("https://directory.example");
        remote.setTokenEnvName("TEST_DIRECTORY_TOKEN");
        remote.setAllowedTools(List.of("findDepartment"));
        properties.setRemoteServers(List.of(remote));
        McpClientFactory factory = new McpClientFactory(new ObjectMapper(), properties) {
            @Override public McpServerConnection connect(McpProperties.RemoteServer ignored) { return McpFailureIsolationIT.this.connect(); }
        };
        return new McpDiscoveryService(registry, properties, factory, connections, health);
    }

    private ToolDescriptor descriptor(String name, List<String> agents) {
        return new ToolDescriptor(name, "1", name, ToolSourceType.LOCAL,
                Map.of("type", "object", "properties", Map.of()), Map.of(), ToolRisk.LOW,
                List.of("USER"), agents, Duration.ofSeconds(1), true, false, "INTERNAL");
    }

    private GatewayToolRequest remoteRequest() {
        return new GatewayToolRequest("mcp.directory.findDepartment@1", Map.of("symptoms", "cough"),
                new GatewayExecutionContext(1L, List.of("USER"), "triage", null, null, null, null));
    }

    private GatewayToolRequest localRequest() {
        return new GatewayToolRequest("local.searchKnowledge@1", Map.of(),
                new GatewayExecutionContext(1L, List.of("USER"), "knowledge", null, null, null, null));
    }

    private void startFixture() {
        server = new SpringApplication(Fixture.class).run("--server.port=0",
                "--spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration,org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration,org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration");
    }

    private McpServerConnection connect() {
        int port = ((WebServerApplicationContext) server).getWebServer().getPort();
        var transport = HttpClientStreamableHttpTransport.builder("http://localhost:" + port)
                .endpoint("/mcp").jsonMapper(new JacksonMcpJsonMapper(new ObjectMapper())).connectTimeout(Duration.ofSeconds(2)).build();
        McpSyncClient connected = McpClient.sync(transport).clientInfo(new McpSchema.Implementation("isolation-test", "1")).build();
        connected.initialize();
        client = connected;
        return new McpServerConnection() {
            @Override public String serverName() { return "directory"; }
            @Override public List<RemoteMcpTool> listTools() { return connected.listTools().tools().stream()
                    .map(tool -> new RemoteMcpTool(tool.name(), tool.description(), tool.inputSchema(), tool.outputSchema())).toList(); }
            @Override public Object callTool(String name, Map<String, Object> arguments) {
                McpSchema.CallToolResult result = connected.callTool(new McpSchema.CallToolRequest(name, arguments));
                if (result.isError()) throw new IllegalStateException("MCP remote unavailable");
                return result.structuredContent();
            }
        };
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class Fixture {
        @Bean HttpServletStreamableServerTransportProvider transport(ObjectMapper mapper) {
            return HttpServletStreamableServerTransportProvider.builder().mcpEndpoint("/mcp")
                    .jsonMapper(new JacksonMcpJsonMapper(mapper)).disallowDelete(true).build();
        }

        @Bean McpSyncServer mcpServer(HttpServletStreamableServerTransportProvider transport) {
            McpSchema.Tool tool = new McpSchema.Tool("findDepartment", null, "directory", Map.of("type", "object"),
                    Map.of("type", "object"), null, Map.of());
            return McpServer.sync(transport).serverInfo("directory", "1")
                    .toolCall(tool, (exchange, request) -> new McpSchema.CallToolResult(List.of(), false,
                            Map.of("department", "RESP"), Map.of())).build();
        }

        @Bean ServletRegistrationBean<HttpServletStreamableServerTransportProvider> servlet(HttpServletStreamableServerTransportProvider transport) {
            return new ServletRegistrationBean<>(transport, "/mcp");
        }
    }
}
