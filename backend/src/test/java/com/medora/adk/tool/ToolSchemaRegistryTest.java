package com.medora.adk.tool;

import com.google.genai.types.FunctionDeclaration;
import com.google.genai.types.Schema;
import com.google.genai.types.Type;
import com.medora.agent.tool.ToolNames;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ToolSchemaRegistry 单测:验证 7 个工具的 schema 描述完整且正确。
 *
 * 重点验证项:
 *  - 所有 7 个工具都有 schema 注册
 *  - 高风险工具 bookAppointment/cancelAppointment 的 required 字段标注正确(LLM 必填)
 *  - enum 字段的枚举值正确暴露给 LLM(指导其取值范围)
 *  - get(unknown) 抛 NoSuchElementException fail-fast
 */
class ToolSchemaRegistryTest {

    private final ToolSchemaRegistry registry = new ToolSchemaRegistry();

    @Test
    void allSevenToolsAreRegistered() {
        assertThat(registry.size()).isEqualTo(7);
        assertThat(registry.has(ToolNames.QUERY_DEPARTMENTS)).isTrue();
        assertThat(registry.has(ToolNames.QUERY_DOCTORS)).isTrue();
        assertThat(registry.has(ToolNames.QUERY_SCHEDULE_SLOTS)).isTrue();
        assertThat(registry.has(ToolNames.GET_USER_APPOINTMENTS)).isTrue();
        assertThat(registry.has(ToolNames.PREPARE_APPOINTMENT)).isTrue();
        assertThat(registry.has(ToolNames.CANCEL_APPOINTMENT)).isTrue();
        assertThat(registry.has(ToolNames.SEARCH_KNOWLEDGE)).isTrue();
    }

    @Test
    void getUnknownThrows() {
        assertThatThrownBy(() -> registry.get("nonexistent_tool"))
                .isInstanceOf(java.util.NoSuchElementException.class)
                .hasMessageContaining("nonexistent_tool");
    }

    @Test
    void prepareAppointmentRequiresSlotId() {
        ToolSchemaDescriptor desc = registry.get(ToolNames.PREPARE_APPOINTMENT);
        assertThat(desc.required()).containsExactly("slotId");
        assertThat(desc.parameters()).containsOnlyKeys("slotId");
        assertThat(desc.parameters().get("slotId").type()).isEqualTo(Type.Known.STRING);
    }

    @Test
    void prepareAppointmentIsTheOnlyAgentBookingEntryPoint() {
        assertThat(registry.has("prepareAppointment")).isTrue();
        assertThat(registry.has("bookAppointment")).isFalse();
    }

    @Test
    void cancelAppointmentRequiresAppointmentId() {
        ToolSchemaDescriptor desc = registry.get(ToolNames.CANCEL_APPOINTMENT);
        assertThat(desc.required()).containsExactly("appointmentId");
        assertThat(desc.parameters()).containsOnlyKeys("appointmentId", "reason");
    }

    @Test
    void queryScheduleSlotsRequiresScheduleDate() {
        ToolSchemaDescriptor desc = registry.get(ToolNames.QUERY_SCHEDULE_SLOTS);
        assertThat(desc.required()).containsExactly("scheduleDate");
        assertThat(desc.parameters().get("periodCode").enumValues())
                .containsExactly("AM", "PM");
    }

    @Test
    void getUserAppointmentsStatusIsEnum() {
        ToolSchemaDescriptor desc = registry.get(ToolNames.GET_USER_APPOINTMENTS);
        assertThat(desc.parameters().get("status").enumValues())
                .containsExactlyInAnyOrder("PENDING", "CONFIRMED", "CANCELLED", "COMPLETED", "NO_SHOW");
        assertThat(desc.required()).isEmpty();
    }

    @Test
    void buildParametersSchemaProducesValidObjectSchema() {
        ToolSchemaDescriptor desc = registry.get(ToolNames.QUERY_DEPARTMENTS);
        Schema schema = desc.buildParametersSchema();
        assertThat(schema.type()).isPresent();
        assertThat(schema.type().get().knownEnum()).isEqualTo(Type.Known.OBJECT);
        assertThat(schema.properties()).isPresent();
        assertThat(schema.properties().get()).containsKeys("keyword", "pageSize");
        assertThat(schema.propertyOrdering()).isPresent();
        // queryDepartments 无必填字段,required 是 empty list(非 null),包在 Optional 中
        assertThat(schema.required()).isPresent();
        assertThat(schema.required().get()).isEmpty();
    }

    @Test
    void buildParametersSchemaWithRequired() {
        ToolSchemaDescriptor desc = registry.get(ToolNames.PREPARE_APPOINTMENT);
        Schema schema = desc.buildParametersSchema();
        assertThat(schema.required()).isPresent();
        java.util.List<String> required = schema.required().get();
        assertThat(required).containsExactly("slotId");
        assertThat(schema.properties().get().get("slotId").type()).isPresent();
        assertThat(schema.properties().get().get("slotId").type().get().knownEnum())
                .isEqualTo(Type.Known.STRING);
    }

    @Test
    void enumValuesPropagateToSchema() {
        ToolSchemaDescriptor desc = registry.get(ToolNames.QUERY_SCHEDULE_SLOTS);
        Schema schema = desc.buildParametersSchema();
        Schema periodSchema = schema.properties().get().get("periodCode");
        assertThat(periodSchema.enum_()).isPresent();
        java.util.List<String> enums = periodSchema.enum_().get();
        assertThat(enums).containsExactly("AM", "PM");
    }

    /** sanity:每个工具的描述非空,name 与 ToolNames 常量一致 */
    @Test
    void allDescriptorsHaveNameAndDescription() {
        List.of(ToolNames.QUERY_DEPARTMENTS, ToolNames.QUERY_DOCTORS, ToolNames.QUERY_SCHEDULE_SLOTS,
                        ToolNames.GET_USER_APPOINTMENTS, ToolNames.PREPARE_APPOINTMENT,
                        ToolNames.CANCEL_APPOINTMENT, ToolNames.SEARCH_KNOWLEDGE)
                .forEach(name -> {
                    ToolSchemaDescriptor d = registry.get(name);
                    assertThat(d.name()).isEqualTo(name);
                    assertThat(d.description()).isNotBlank();
                });
    }
}
