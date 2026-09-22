package com.medora.mcp.client;

import java.util.List;
import java.util.Map;

public interface McpServerConnection {
    String serverName();

    List<RemoteMcpTool> listTools();

    Object callTool(String name, Map<String, Object> arguments);

    @FunctionalInterface
    interface Call {
        Object call(String name, Map<String, Object> arguments);
    }
}
