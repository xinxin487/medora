package com.medora.toolgateway.schema;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

@Component
public class ToolSchemaValidator {
    private final ObjectMapper objectMapper;
    private final Schema schema;
    public ToolSchemaValidator(ObjectMapper objectMapper) { this.objectMapper = objectMapper; this.schema = null; }
    private ToolSchemaValidator(ObjectMapper objectMapper, Schema schema) { this.objectMapper = objectMapper; this.schema = schema; }
    public static ToolSchemaValidator fromClasspath(ObjectMapper mapper, String schemaName) {
        try (InputStream stream = ToolSchemaValidator.class.getResourceAsStream("/tool-schemas/" + schemaName + ".json")) {
            if (stream == null) throw new IllegalArgumentException("schema not found: " + schemaName);
            JsonNode node = mapper.readTree(stream);
            return new ToolSchemaValidator(mapper, SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12).getSchema(node));
        } catch (Exception ex) { throw new IllegalStateException("cannot load schema " + schemaName, ex); }
    }
    public List<String> validate(Map<String, Object> arguments) {
        if (schema == null) throw new IllegalStateException("a concrete tool schema is required");
        return schema.validate(objectMapper.valueToTree(arguments), context ->
                context.executionConfig(config -> config.formatAssertionsEnabled(true)))
                .stream().map(Object::toString).toList();
    }
    public List<String> validate(Map<String, Object> schemaDefinition, Map<String, Object> arguments) {
        Schema compiled = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12)
                .getSchema(objectMapper.valueToTree(schemaDefinition));
        return compiled.validate(objectMapper.valueToTree(arguments), context ->
                context.executionConfig(config -> config.formatAssertionsEnabled(true)))
                .stream().map(Object::toString).toList();
    }
}
