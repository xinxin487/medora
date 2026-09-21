package com.medora.toolgateway.adapter;
import com.medora.toolgateway.execution.GatewayToolRequest;
public interface ToolAdapter { boolean supports(String qualifiedToolName); Object execute(GatewayToolRequest request); }
