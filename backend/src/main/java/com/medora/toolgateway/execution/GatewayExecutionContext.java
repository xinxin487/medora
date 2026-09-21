package com.medora.toolgateway.execution;
import java.util.List;
public record GatewayExecutionContext(Long userId, List<String> roles, String agentName, String conversationId,
                                      String traceId, String requestId, String confirmationActionId) {
    public GatewayExecutionContext { roles = roles == null ? List.of() : List.copyOf(roles); }
    public static GatewayExecutionContext anonymous() { return new GatewayExecutionContext(null, List.of(), null, null, null, null, null); }
    public GatewayExecutionContext withConfirmationActionId(String actionId) { return new GatewayExecutionContext(userId, roles, agentName, conversationId, traceId, requestId, actionId); }
}
