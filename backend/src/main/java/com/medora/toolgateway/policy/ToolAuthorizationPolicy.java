package com.medora.toolgateway.policy;
import com.medora.toolgateway.descriptor.ToolDescriptor;
import com.medora.toolgateway.execution.GatewayExecutionContext;
import org.springframework.stereotype.Component;
@Component public class ToolAuthorizationPolicy {
    public boolean permits(ToolDescriptor descriptor, GatewayExecutionContext context) {
        if (context == null || context.userId() == null) return descriptor.requiredRoles().isEmpty();
        return context.roles().containsAll(descriptor.requiredRoles()) && descriptor.allowedAgents().contains(context.agentName());
    }
}
