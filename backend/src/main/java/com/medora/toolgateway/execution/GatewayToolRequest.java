package com.medora.toolgateway.execution;
import java.util.Map;
public record GatewayToolRequest(String qualifiedToolName, Map<String, Object> arguments, GatewayExecutionContext context) { public GatewayToolRequest { arguments = arguments == null ? Map.of() : Map.copyOf(arguments); } }
