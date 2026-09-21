package com.medora.toolgateway.audit;

import com.medora.toolgateway.descriptor.ToolDescriptor;
import com.medora.toolgateway.execution.GatewayToolRequest;
import com.medora.toolgateway.execution.GatewayToolResult;

@FunctionalInterface
public interface ToolAuditRecorder {

    void record(ToolDescriptor descriptor, GatewayToolRequest request, GatewayToolResult result, long durationMs);

    static ToolAuditRecorder noop() {
        return (descriptor, request, result, durationMs) -> { };
    }
}
