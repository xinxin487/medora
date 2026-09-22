package com.medora.mcp.server;

import com.medora.toolgateway.execution.GatewayExecutionContext;
import com.medora.toolgateway.execution.GatewayToolRequest;
import com.medora.toolgateway.execution.GatewayToolResult;
import com.medora.toolgateway.execution.ToolGateway;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class McpReadOnlyToolTest {
    @Test
    void knowledgeToolUsesGatewayWithFixedReadOnlyIdentity() {
        ToolGateway gateway = mock(ToolGateway.class);
        when(gateway.execute(any())).thenReturn(GatewayToolResult.success(Map.of("items", java.util.List.of())));

        Object result = new SearchMedicalKnowledgeMcpTool(gateway).execute(Map.of("query", "cough"));

        assertThat(result).isEqualTo(Map.of("items", java.util.List.of()));
        ArgumentCaptor<GatewayToolRequest> request = ArgumentCaptor.forClass(GatewayToolRequest.class);
        verify(gateway).execute(request.capture());
        assertThat(request.getValue().qualifiedToolName()).isEqualTo("local.searchKnowledge@1");
        assertThat(request.getValue().arguments()).containsEntry("query", "cough");
        assertThat(request.getValue().context()).isEqualTo(new GatewayExecutionContext(0L, java.util.List.of("USER", "MCP_SERVICE"), "knowledge", null, null, null, null));
    }

    @Test
    void scheduleToolUsesGatewayWithFixedReadOnlyIdentity() {
        ToolGateway gateway = mock(ToolGateway.class);
        when(gateway.execute(any())).thenReturn(GatewayToolResult.success(Map.of("items", java.util.List.of())));

        Object result = new QueryScheduleSlotsMcpTool(gateway).execute(Map.of("scheduleDate", "2026-09-23"));

        assertThat(result).isEqualTo(Map.of("items", java.util.List.of()));
        ArgumentCaptor<GatewayToolRequest> request = ArgumentCaptor.forClass(GatewayToolRequest.class);
        verify(gateway).execute(request.capture());
        assertThat(request.getValue().qualifiedToolName()).isEqualTo("local.queryScheduleSlots@1");
        assertThat(request.getValue().context()).isEqualTo(new GatewayExecutionContext(0L, java.util.List.of("USER", "MCP_SERVICE"), "triage", null, null, null, null));
    }

    @Test
    void gatewayFailureDoesNotExposeItsMessage() {
        ToolGateway gateway = mock(ToolGateway.class);
        when(gateway.execute(any())).thenReturn(GatewayToolResult.failure(GatewayToolResult.Status.FAILED, null, "database secret leaked", false));

        assertThatThrownBy(() -> new SearchMedicalKnowledgeMcpTool(gateway).execute(Map.of("query", "cough")))
                .hasMessage("tool unavailable")
                .satisfies(error -> assertThat(error.getMessage()).doesNotContain("database secret"));
    }
}
