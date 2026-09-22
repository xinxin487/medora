package com.medora.mcpdemo;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.json.jackson2.JacksonMcpJsonMapper;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import java.time.Duration;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = HospitalDirectoryApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"hospital-directory.token=test-token", "hospital-directory.allowed-origin=https://medora.example"})
class HospitalDirectoryApplicationIT {
    @LocalServerPort int port;
    @Test void exposesOnlyTheAuthenticatedFindDepartmentToolOverStreamableHttp() {
        HttpClientStreamableHttpTransport transport = HttpClientStreamableHttpTransport.builder("http://localhost:" + port + "/mcp")
                .jsonMapper(new JacksonMcpJsonMapper(new ObjectMapper()))
                .httpRequestCustomizer((request, method, uri, body, context) -> request.header("Authorization", "Bearer test-token").header("Origin", "https://medora.example"))
                .connectTimeout(Duration.ofSeconds(3)).build();
        try (McpSyncClient client = McpClient.sync(transport).clientInfo(new McpSchema.Implementation("test", "1")).build()) {
            client.initialize();
            assertThat(client.listTools().tools()).extracting(McpSchema.Tool::name).containsExactly("findDepartment");
            McpSchema.CallToolResult result = client.callTool(new McpSchema.CallToolRequest("findDepartment", Map.of("symptoms", "咳嗽")));
            assertThat(result.isError()).isFalse();
            assertThat(result.content()).singleElement().isInstanceOf(McpSchema.TextContent.class);
        }
    }

    @Test void rejectsAMissingBearerTokenBeforeProtocolProcessing() throws Exception {
        HttpResponse<String> response = rawRequest(null, "https://medora.example");
        assertThat(response.statusCode()).isEqualTo(401);
    }

    @Test void rejectsAnUnapprovedOriginBeforeProtocolProcessing() throws Exception {
        HttpResponse<String> response = rawRequest("Bearer test-token", "https://unapproved.example");
        assertThat(response.statusCode()).isEqualTo(403);
    }

    private HttpResponse<String> rawRequest(String authorization, String origin) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/mcp"))
                .POST(HttpRequest.BodyPublishers.ofString("{}"))
                .header("Content-Type", "application/json").header("Origin", origin);
        if (authorization != null) request.header("Authorization", authorization);
        return HttpClient.newHttpClient().send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}
