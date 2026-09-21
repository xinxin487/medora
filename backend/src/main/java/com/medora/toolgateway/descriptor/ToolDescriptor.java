package com.medora.toolgateway.descriptor;
import java.time.Duration;
import java.util.List;
import java.util.Map;
public record ToolDescriptor(String name, String version, String description, ToolSourceType sourceType,
                             Map<String, Object> inputSchema, Map<String, Object> outputSchema, ToolRisk riskLevel,
                             List<String> requiredRoles, List<String> allowedAgents, Duration timeout,
                             boolean idempotent, boolean requiresConfirmation, String dataClassification) {
    public ToolDescriptor { inputSchema = Map.copyOf(inputSchema); outputSchema = Map.copyOf(outputSchema); requiredRoles = List.copyOf(requiredRoles); allowedAgents = List.copyOf(allowedAgents); }
    public String qualifiedName() { return name + "@" + version; }
}
