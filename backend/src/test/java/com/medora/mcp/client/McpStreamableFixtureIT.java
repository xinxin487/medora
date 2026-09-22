package com.medora.mcp.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.json.jackson2.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.HttpServletStreamableServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** A real local Streamable HTTP boundary for MCP SDK compatibility tests. */
@SpringBootTest(classes = McpStreamableFixtureIT.Fixture.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration,org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration,org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration"})
class McpStreamableFixtureIT {
    @LocalServerPort int port;

    @Test
    void initializesListsAndCallsTheRemoteToolOverHttp() {
        var transport = HttpClientStreamableHttpTransport.builder("http://localhost:" + port)
                .jsonMapper(new JacksonMcpJsonMapper(new ObjectMapper()))
                .endpoint("/fixture-mcp")
                .connectTimeout(Duration.ofSeconds(3)).build();
        try (McpSyncClient client = McpClient.sync(transport).clientInfo(new McpSchema.Implementation("fixture-client", "1")).build()) {
            client.initialize();
            assertThat(client.listTools().tools()).extracting(McpSchema.Tool::name).containsExactly("findDepartment");
            var result = client.callTool(new McpSchema.CallToolRequest("findDepartment", Map.of("symptoms", "cough")));
            assertThat(result.isError()).isFalse();
            assertThat(result.structuredContent()).isEqualTo(Map.of("department", "RESP"));
        }
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class Fixture {
        @Bean HttpServletStreamableServerTransportProvider fixtureTransport(ObjectMapper mapper) {
            return HttpServletStreamableServerTransportProvider.builder().mcpEndpoint("/fixture-mcp")
                    .jsonMapper(new JacksonMcpJsonMapper(mapper)).disallowDelete(true).build();
        }

        @Bean McpSyncServer fixtureServer(HttpServletStreamableServerTransportProvider transport) {
            Map<String, Object> input = Map.of("type", "object", "properties", Map.of("symptoms", Map.of("type", "string")), "required", List.of("symptoms"));
            McpSchema.Tool tool = new McpSchema.Tool("findDepartment", null, "fixture department lookup", input, Map.of("type", "object"), null, Map.of());
            return McpServer.sync(transport).serverInfo("fixture", "1").toolCall(tool,
                    (exchange, request) -> new McpSchema.CallToolResult(List.of(), false, Map.of("department", "RESP"), Map.of())).build();
        }

        @Bean ServletRegistrationBean<HttpServletStreamableServerTransportProvider> fixtureServlet(HttpServletStreamableServerTransportProvider transport) {
            return new ServletRegistrationBean<>(transport, "/fixture-mcp");
        }
    }
}
