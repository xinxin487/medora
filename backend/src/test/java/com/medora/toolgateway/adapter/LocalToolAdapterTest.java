package com.medora.toolgateway.adapter;

import com.medora.agent.tool.*;
import com.medora.toolgateway.execution.*;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class LocalToolAdapterTest {
    @Test void delegatesLocalDescriptorToExistingDefinitionWithSafeContext() {
        ToolDefinition definition = mock(ToolDefinition.class);
        when(definition.name()).thenReturn("searchKnowledge");
        when(definition.execute(eq(Map.of("query", "hours")), any())).thenReturn(ToolExecutionResult.success("searchKnowledge", Map.of("items", 1), "ok"));
        ToolRegistry registry = new ToolRegistry(java.util.List.of(definition));
        LocalToolAdapter adapter = new LocalToolAdapter(registry);
        Object result = adapter.execute(new GatewayToolRequest("local.searchKnowledge@1", Map.of("query", "hours"), new GatewayExecutionContext(7L, java.util.List.of("USER"), "knowledge", "c", "trace", "request", null)));
        assertThat(result).isInstanceOf(ToolExecutionResult.class);
        verify(definition).execute(eq(Map.of("query", "hours")), argThat(context -> context.userAccountId().equals(7L) && context.traceId().equals("trace")));
    }
}
