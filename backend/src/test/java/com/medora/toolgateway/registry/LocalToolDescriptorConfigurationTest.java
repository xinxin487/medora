package com.medora.toolgateway.registry;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medora.agent.tool.ToolDefinition;
import com.medora.agent.tool.ToolExecutionContext;
import com.medora.agent.tool.ToolExecutionResult;
import com.medora.agent.tool.ToolNames;
import com.medora.agent.tool.ToolRiskLevel;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class LocalToolDescriptorConfigurationTest {

    @Test
    void loadsSchemasForThePrepareAppointmentToolNameUsedAtRuntime() {
        ToolDefinition prepareAppointment = new ToolDefinition() {
            @Override public String name() { return ToolNames.PREPARE_APPOINTMENT; }
            @Override public String description() { return "fixture"; }
            @Override public ToolRiskLevel riskLevel() { return ToolRiskLevel.HIGH; }
            @Override public ToolExecutionResult execute(Map<String, Object> arguments, ToolExecutionContext context) {
                throw new UnsupportedOperationException("not used");
            }
        };

        var descriptors = new LocalToolDescriptorConfiguration()
                .localToolDescriptors(List.of(prepareAppointment), new ObjectMapper());

        assertThat(descriptors).singleElement().satisfies(descriptor -> {
            assertThat(descriptor.qualifiedName()).isEqualTo("local.prepareAppointment@1");
            assertThat(descriptor.inputSchema()).containsEntry("type", "object");
            assertThat(descriptor.inputSchema()).containsKey("properties");
        });
    }
}
