package com.medora.mcp.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medora.mcp.config.McpProperties;
import com.medora.mcp.config.McpProtocolConfig;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.json.jackson2.JacksonMcpJsonMapper;
import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/** Creates Streamable HTTP clients from pre-validated, server-side configuration only. */
@Component
public class McpClientFactory {
    private static final int MAX_TOOLS = 50;
    private static final int MAX_SCHEMA_BYTES = 16 * 1024;
    private static final int MAX_RESPONSE_BYTES = 128 * 1024;
    private final ObjectMapper objectMapper;

    public McpClientFactory(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public McpServerConnection connect(McpProperties.RemoteServer remote) {
        // McpProperties validates the configuration and its remote host allow-list at startup.
        String token = System.getenv(remote.getTokenEnvName());
        if (token == null || token.isBlank()) throw new McpConnectionException("MCP remote unavailable");
        HttpClientStreamableHttpTransport transport = HttpClientStreamableHttpTransport.builder(remote.getBaseUrl())
                .jsonMapper(new JacksonMcpJsonMapper(objectMapper))
                .supportedProtocolVersions(List.of(McpProtocolConfig.PROTOCOL_VERSION))
                .connectTimeout(Duration.ofSeconds(3))
                .httpRequestCustomizer((request, method, uri, body, context) -> request.header("Authorization", "Bearer " + token))
                .build();
        McpSyncClient client = McpClient.sync(transport)
                .clientInfo(new McpSchema.Implementation("medora", "1.0.0"))
                .requestTimeout(Duration.ofSeconds(3))
                .initializationTimeout(Duration.ofSeconds(3))
                .build();
        try {
            client.initialize();
            return new SdkConnection(remote.getName(), client);
        } catch (RuntimeException exception) {
            client.close();
            throw new McpConnectionException("MCP remote unavailable");
        }
    }

    private final class SdkConnection implements McpServerConnection {
        private final String serverName;
        private final McpSyncClient client;

        private SdkConnection(String serverName, McpSyncClient client) {
            this.serverName = serverName;
            this.client = client;
        }

        @Override public String serverName() { return serverName; }

        @Override public List<RemoteMcpTool> listTools() {
            List<McpSchema.Tool> tools = client.listTools().tools();
            if (tools.size() > MAX_TOOLS) throw new McpConnectionException("MCP remote unavailable");
            return tools.stream().map(tool -> {
                cap(tool.inputSchema()); cap(tool.outputSchema());
                return new RemoteMcpTool(tool.name(), tool.description(), tool.inputSchema(), tool.outputSchema());
            }).toList();
        }

        @Override public Object callTool(String name, Map<String, Object> arguments) {
            McpSchema.CallToolResult result = client.callTool(new McpSchema.CallToolRequest(name, arguments));
            if (Boolean.TRUE.equals(result.isError())) throw new McpConnectionException("MCP remote unavailable");
            Object value = result.structuredContent();
            if (value == null && result.content().size() == 1 && result.content().getFirst() instanceof McpSchema.TextContent text) {
                try { value = objectMapper.readValue(text.text(), Object.class); }
                catch (Exception exception) { throw new McpConnectionException("MCP remote unavailable"); }
            }
            if (value == null) throw new McpConnectionException("MCP remote unavailable");
            cap(value);
            return value;
        }

        private void cap(Object value) {
            try {
                if (objectMapper.writeValueAsBytes(value).length > MAX_RESPONSE_BYTES) throw new McpConnectionException("MCP remote unavailable");
            } catch (McpConnectionException exception) { throw exception; }
            catch (Exception exception) { throw new McpConnectionException("MCP remote unavailable"); }
        }

        private void cap(Map<String, Object> schema) {
            try {
                if (objectMapper.writeValueAsBytes(schema).length > MAX_SCHEMA_BYTES) throw new McpConnectionException("MCP remote unavailable");
            } catch (McpConnectionException exception) { throw exception; }
            catch (Exception exception) { throw new McpConnectionException("MCP remote unavailable"); }
        }
    }

    public static class McpConnectionException extends RuntimeException {
        public McpConnectionException(String message) { super(message, null, false, false); }
    }
}
