package com.medora.toolgateway.adapter;
import com.medora.agent.tool.*;
import com.medora.toolgateway.execution.GatewayToolRequest;
import org.springframework.stereotype.Component;
@Component public class LocalToolAdapter implements ToolAdapter {
    private final ToolRegistry registry;
    public LocalToolAdapter(ToolRegistry registry) { this.registry = registry; }
    public boolean supports(String qualifiedToolName) { return qualifiedToolName != null && qualifiedToolName.startsWith("local.") && qualifiedToolName.endsWith("@1"); }
    public Object execute(GatewayToolRequest request) {
        String name = request.qualifiedToolName().substring("local.".length(), request.qualifiedToolName().length() - 2);
        ToolExecutionContext context = new ToolExecutionContext(request.context().userId(), null, request.context().traceId());
        return registry.get(name).execute(request.arguments(), context);
    }
}
