package com.medora.mcp.server;

import com.medora.toolgateway.execution.GatewayExecutionContext;
import com.medora.toolgateway.execution.GatewayToolRequest;
import com.medora.toolgateway.execution.GatewayToolResult;
import com.medora.toolgateway.execution.ToolGateway;

import java.util.Map;

/** Restricts MCP schedule lookup to the existing low-risk Gateway tool. */
public final class QueryScheduleSlotsMcpTool {
    private static final GatewayExecutionContext CONTEXT = new GatewayExecutionContext(
            0L, java.util.List.of("USER", "MCP_SERVICE"), "triage", null, null, null, null);
    private final ToolGateway gateway;

    public QueryScheduleSlotsMcpTool(ToolGateway gateway) {
        this.gateway = gateway;
    }

    public Object execute(Map<String, Object> arguments) {
        GatewayToolResult result = gateway.execute(new GatewayToolRequest("local.queryScheduleSlots@1", arguments, CONTEXT));
        if (result.status() != GatewayToolResult.Status.SUCCESS) {
            throw new IllegalStateException("tool unavailable");
        }
        return result.data();
    }
}
