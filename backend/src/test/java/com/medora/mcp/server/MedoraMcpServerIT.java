package com.medora.mcp.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medora.mcp.config.McpProperties;
import com.medora.toolgateway.execution.GatewayToolResult;
import com.medora.toolgateway.execution.ToolGateway;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.json.jackson2.JacksonMcpJsonMapper;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = MedoraMcpServerIT.Fixture.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "medora.mcp.enabled=true",
                "medora.mcp.server-token=test-token",
                "medora.mcp.allowed-origins[0]=https://allowed.example",
                "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration,org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration,org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration"
        })
class MedoraMcpServerIT {
    @LocalServerPort int port;
    @Autowired ToolGateway gateway;

    @Test
    void rejectsMissingOrWrongBearerTokenBeforeMcpProcessing() throws Exception {
        assertThat(rawRequest(null, "https://allowed.example", "{}").statusCode()).isEqualTo(401);
        assertThat(rawRequest("Bearer wrong", "https://allowed.example", "{}").statusCode()).isEqualTo(401);
    }

    @Test
    void rejectsDisallowedOriginBeforeMcpProcessing() throws Exception {
        assertThat(rawRequest("Bearer test-token", "https://unapproved.example", "{}").statusCode()).isEqualTo(403);
    }

    @Test
    void exposesOnlyTwoReadToolsAndCallsThemOverStreamableHttp() {
        when(gateway.execute(any())).thenReturn(GatewayToolResult.success(Map.of("items", List.of())));

        try (McpSyncClient client = client()) {
            client.initialize();
            assertThat(client.listTools().tools()).extracting(McpSchema.Tool::name)
                    .containsExactlyInAnyOrder("searchMedicalKnowledge", "queryScheduleSlots")
                    .doesNotContain("prepareAppointment", "cancelAppointment");
            McpSchema.CallToolResult knowledge = client.callTool(new McpSchema.CallToolRequest("searchMedicalKnowledge", Map.of("query", "cough")));
            assertThat(knowledge.isError()).as(knowledge.toString()).isFalse();
            assertThat(knowledge.structuredContent()).isEqualTo(Map.of("items", List.of()));
            McpSchema.CallToolResult schedules = client.callTool(new McpSchema.CallToolRequest("queryScheduleSlots", Map.of("scheduleDate", "2026-09-23")));
            assertThat(schedules.isError()).as(schedules.toString()).isFalse();
            assertThat(schedules.structuredContent()).isEqualTo(Map.of("items", List.of()));
        }
    }

    @Test
    void rejectsOversizedAuthenticatedRequests() throws Exception {
        assertThat(rawRequest("Bearer test-token", "https://allowed.example", "x".repeat(65_537)).statusCode()).isEqualTo(413);
    }

    private McpSyncClient client() {
        var transport = HttpClientStreamableHttpTransport.builder("http://localhost:" + port)
                .endpoint("/mcp")
                .jsonMapper(new JacksonMcpJsonMapper(new ObjectMapper()))
                .httpRequestCustomizer((request, method, uri, body, context) -> request
                        .header("Authorization", "Bearer test-token").header("Origin", "https://allowed.example"))
                .connectTimeout(Duration.ofSeconds(3)).build();
        return McpClient.sync(transport).clientInfo(new McpSchema.Implementation("medora-mcp-it", "1")).build();
    }

    private HttpResponse<String> rawRequest(String authorization, String origin, String body) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/mcp"))
                .POST(HttpRequest.BodyPublishers.ofString(body)).header("Content-Type", "application/json").header("Origin", origin);
        if (authorization != null) request.header("Authorization", authorization);
        return HttpClient.newHttpClient().send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EnableConfigurationProperties(McpProperties.class)
    @Import({MedoraMcpServerConfig.class, McpRequestSecurityFilter.class})
    static class Fixture {
        @Bean ToolGateway toolGateway() { return mock(ToolGateway.class); }
    }
}
