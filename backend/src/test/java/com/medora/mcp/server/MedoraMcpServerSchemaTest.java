package com.medora.mcp.server;

import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Method;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class MedoraMcpServerSchemaTest {
    @Test void declaresRequiredFieldsForBothReadOnlyMcpTools() throws Exception {
        MedoraMcpServerConfig config = new MedoraMcpServerConfig();
        for (String methodName : List.of("knowledgeTool", "scheduleTool")) {
            Method method = MedoraMcpServerConfig.class.getDeclaredMethod(methodName);
            method.setAccessible(true);
            McpSchema.Tool tool = (McpSchema.Tool) method.invoke(config);
            assertThat(tool.inputSchema()).containsEntry("additionalProperties", false).containsKey("required");
        }
    }
}
