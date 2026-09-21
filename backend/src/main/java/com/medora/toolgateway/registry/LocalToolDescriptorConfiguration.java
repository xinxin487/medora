package com.medora.toolgateway.registry;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medora.agent.tool.ToolDefinition;
import com.medora.agent.tool.ToolRiskLevel;
import com.medora.toolgateway.descriptor.ToolDescriptor;
import com.medora.toolgateway.descriptor.ToolRisk;
import com.medora.toolgateway.descriptor.ToolSourceType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.InputStream;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Configuration
class LocalToolDescriptorConfiguration {
    @Bean
    List<ToolDescriptor> localToolDescriptors(List<ToolDefinition> definitions, ObjectMapper objectMapper) {
        return definitions.stream().map(definition -> new ToolDescriptor("local." + definition.name(), "1",
                definition.description(), ToolSourceType.LOCAL, schema(objectMapper, definition.name(), "input"),
                schema(objectMapper, definition.name(), "output"), definition.riskLevel() == ToolRiskLevel.HIGH ? ToolRisk.HIGH : ToolRisk.LOW,
                List.of("USER"), allowedAgents(definition.name()), Duration.ofSeconds(3), definition.riskLevel() != ToolRiskLevel.HIGH,
                definition.riskLevel() == ToolRiskLevel.HIGH, "INTERNAL")).toList();
    }
    private Map<String, Object> schema(ObjectMapper mapper, String camelName, String kind) {
        String kebab = camelName.replaceAll("([a-z])([A-Z])", "$1-$2").toLowerCase();
        try (InputStream stream = getClass().getResourceAsStream("/tool-schemas/" + kebab + "-" + kind + ".json")) {
            if (stream == null) throw new IllegalStateException("missing schema: " + kebab + "-" + kind);
            return mapper.readValue(stream, new TypeReference<>() {});
        } catch (Exception ex) { throw new IllegalStateException("cannot load schema for " + camelName, ex); }
    }
    private List<String> allowedAgents(String name) { return name.equals("searchKnowledge") ? List.of("knowledge", "triage") : List.of("booking", "triage"); }
}
