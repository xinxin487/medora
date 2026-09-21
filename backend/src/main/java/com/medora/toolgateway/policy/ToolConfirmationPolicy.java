package com.medora.toolgateway.policy;
import com.medora.toolgateway.descriptor.ToolDescriptor;
import com.medora.toolgateway.execution.GatewayExecutionContext;
import org.springframework.stereotype.Component;
@Component public class ToolConfirmationPolicy {
    public boolean confirmed(ToolDescriptor descriptor, GatewayExecutionContext context) { return !descriptor.requiresConfirmation() || (context != null && context.confirmationActionId() != null && !context.confirmationActionId().isBlank()); }
}
