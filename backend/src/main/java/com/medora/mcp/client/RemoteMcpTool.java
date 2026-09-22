package com.medora.mcp.client;

import java.util.Map;

/** A remote MCP tool definition after protocol discovery, before gateway registration. */
public record RemoteMcpTool(String name, String description, Map<String, Object> inputSchema,
                            Map<String, Object> outputSchema) {
    public RemoteMcpTool {
        inputSchema = Map.copyOf(inputSchema);
        outputSchema = Map.copyOf(outputSchema);
    }
}
