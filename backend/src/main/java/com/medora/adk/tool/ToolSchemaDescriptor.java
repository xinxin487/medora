package com.medora.adk.tool;

import com.google.genai.types.Schema;
import com.google.genai.types.Type;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 单个工具的声明式 schema 描述 — 由 ToolSchemaRegistry 静态持有,供 MedoraFunctionTool 构建
 * FunctionDeclaration 使用。
 *
 * 设计理由:
 *  - 不使用 ADK FunctionTool.create(Object, String) 反射方案,因为它要求被反射的方法签名
 *    只能包含 LLM 可填的字段,无法承载 ToolExecutionContext 这种「LLM 不可见但工具需要」的上下文
 *  - 直接子类化 BaseTool + 手写 schema 给我们完全的控制权:每个工具的入参字段、类型、必填、
 *    enum 都明确,且 ToolExecutionContext 从 session state 注入,不出现在 LLM schema 中
 */
public record ToolSchemaDescriptor(
        String name,
        String description,
        /** 字段名 → schema;LinkedHashMap 保序,使 propertyOrdering 稳定 */
        Map<String, FieldSchema> parameters,
        List<String> required
) {

    public record FieldSchema(
            Type.Known type,
            String description,
            List<String> enumValues,
            boolean required
    ) {
        public static FieldSchema string(String description) {
            return new FieldSchema(Type.Known.STRING, description, null, false);
        }

        public static FieldSchema integer(String description) {
            return new FieldSchema(Type.Known.INTEGER, description, null, false);
        }

        /** 标记为必填(给 LLM 的 required 字段)。返回新实例,immutable 风格 */
        public FieldSchema asRequired() {
            return new FieldSchema(type, description, enumValues, true);
        }

        public FieldSchema withEnumValues(List<String> values) {
            return new FieldSchema(type, description, values, required);
        }
    }

    public static Builder builder(String name, String description) {
        return new Builder(name, description);
    }

    public Schema buildParametersSchema() {
        Map<String, Schema> properties = new LinkedHashMap<>();
        List<String> ordering = new java.util.ArrayList<>();
        parameters.forEach((fieldName, fieldSchema) -> {
            Schema.Builder sb = Schema.builder()
                    .type(fieldSchema.type())
                    .description(fieldSchema.description());
            if (fieldSchema.enumValues() != null && !fieldSchema.enumValues().isEmpty()) {
                sb.enum_(fieldSchema.enumValues());
            }
            properties.put(fieldName, sb.build());
            ordering.add(fieldName);
        });
        return Schema.builder()
                .type(Type.Known.OBJECT)
                .properties(properties)
                .propertyOrdering(ordering)
                .required(required)
                .build();
    }

    public static final class Builder {
        private final String name;
        private final String description;
        private final Map<String, FieldSchema> parameters = new LinkedHashMap<>();
        private final List<String> required = new java.util.ArrayList<>();

        public Builder(String name, String description) {
            this.name = name;
            this.description = description;
        }

        public Builder param(String fieldName, FieldSchema schema) {
            parameters.put(fieldName, schema);
            if (schema.required()) {
                required.add(fieldName);
            }
            return this;
        }

        public ToolSchemaDescriptor build() {
            return new ToolSchemaDescriptor(name, description,
                    Map.copyOf(parameters), List.copyOf(required));
        }
    }
}
