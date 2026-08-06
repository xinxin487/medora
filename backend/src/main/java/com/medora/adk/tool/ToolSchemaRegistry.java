package com.medora.adk.tool;

import com.medora.agent.tool.ToolNames;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.NoSuchElementException;

/**
 * 7 个 ToolDefinition 对应的 ADK schema 描述表 — 静态、声明式、强类型。
 *
 * 每个工具的 schema 与现有 ToolDefinition.execute() 实际读取的字段一一对应(见 ToolArguments.stringValue),
 * 而不是与 ToolExecutor 中的 IntentDecision.ToolInvocation 耦合。这样 ADK 路径与现有手写路径
 * 共用同一组底层 ToolDefinition bean,只是上层调用机制不同。
 *
 * 高风险工具(bookAppointment / cancelAppointment)的字段同时被 HighRiskToolBeforeCallback
 * 校验,其 required 字段必须包含 CallbackKey.USER_CONFIRMED 之外的全部业务字段。
 */
@Component
public class ToolSchemaRegistry {

    private final Map<String, ToolSchemaDescriptor> descriptors;

    public ToolSchemaRegistry() {
        this.descriptors = Map.of(
                ToolNames.QUERY_DEPARTMENTS, queryDepartments(),
                ToolNames.QUERY_DOCTORS, queryDoctors(),
                ToolNames.QUERY_SCHEDULE_SLOTS, queryScheduleSlots(),
                ToolNames.GET_USER_APPOINTMENTS, getUserAppointments(),
                ToolNames.BOOK_APPOINTMENT, bookAppointment(),
                ToolNames.CANCEL_APPOINTMENT, cancelAppointment(),
                ToolNames.SEARCH_KNOWLEDGE, searchKnowledge()
        );
    }

    public ToolSchemaDescriptor get(String toolName) {
        ToolSchemaDescriptor d = descriptors.get(toolName);
        if (d == null) {
            throw new NoSuchElementException("no schema descriptor for tool: " + toolName);
        }
        return d;
    }

    public boolean has(String toolName) {
        return descriptors.containsKey(toolName);
    }

    public int size() {
        return descriptors.size();
    }

    private static ToolSchemaDescriptor queryDepartments() {
        return ToolSchemaDescriptor.builder(ToolNames.QUERY_DEPARTMENTS, "查询可挂号科室")
                .param("keyword", ToolSchemaDescriptor.FieldSchema.string("科室关键词,可空"))
                .param("pageSize", ToolSchemaDescriptor.FieldSchema.integer("每页数量,默认 5"))
                .build();
    }

    private static ToolSchemaDescriptor queryDoctors() {
        return ToolSchemaDescriptor.builder(ToolNames.QUERY_DOCTORS, "查询医生")
                .param("departmentId", ToolSchemaDescriptor.FieldSchema.string("科室 ID,与 departmentKeyword 二选一"))
                .param("departmentKeyword", ToolSchemaDescriptor.FieldSchema.string("科室关键词,departmentId 为空时使用"))
                .param("keyword", ToolSchemaDescriptor.FieldSchema.string("医生姓名关键词"))
                .param("pageSize", ToolSchemaDescriptor.FieldSchema.integer("每页数量,默认 5"))
                .build();
    }

    private static ToolSchemaDescriptor queryScheduleSlots() {
        return ToolSchemaDescriptor.builder(ToolNames.QUERY_SCHEDULE_SLOTS, "查询可预约号源")
                .param("scheduleDate", ToolSchemaDescriptor.FieldSchema.string("日期 yyyy-MM-dd,必填")
                        .asRequired())
                .param("departmentId", ToolSchemaDescriptor.FieldSchema.string("科室 ID"))
                .param("departmentKeyword", ToolSchemaDescriptor.FieldSchema.string("科室关键词"))
                .param("doctorId", ToolSchemaDescriptor.FieldSchema.string("医生 ID"))
                .param("doctorKeyword", ToolSchemaDescriptor.FieldSchema.string("医生关键词"))
                .param("periodCode", ToolSchemaDescriptor.FieldSchema.string("时段,AM/PM,允许自然语言如 上午/下午")
                        .withEnumValues(java.util.List.of("AM", "PM")))
                .param("pageSize", ToolSchemaDescriptor.FieldSchema.integer("每页数量,默认 5"))
                .build();
    }

    private static ToolSchemaDescriptor getUserAppointments() {
        return ToolSchemaDescriptor.builder(ToolNames.GET_USER_APPOINTMENTS, "查询当前用户预约列表")
                .param("status", ToolSchemaDescriptor.FieldSchema.string("预约状态")
                        .withEnumValues(java.util.List.of("PENDING", "CONFIRMED", "CANCELLED", "COMPLETED", "NO_SHOW")))
                .param("scheduleDateFrom", ToolSchemaDescriptor.FieldSchema.string("起始日期 yyyy-MM-dd"))
                .param("scheduleDateTo", ToolSchemaDescriptor.FieldSchema.string("结束日期 yyyy-MM-dd"))
                .param("pageSize", ToolSchemaDescriptor.FieldSchema.integer("每页数量,默认 5"))
                .build();
    }

    private static ToolSchemaDescriptor bookAppointment() {
        return ToolSchemaDescriptor.builder(ToolNames.BOOK_APPOINTMENT, "预约挂号(高风险,需 slotId 由用户提供)")
                .param("slotId", ToolSchemaDescriptor.FieldSchema.string("号源 ID,必填,必须来自上一轮 queryScheduleSlots 的结果")
                        .asRequired())
                .build();
    }

    private static ToolSchemaDescriptor cancelAppointment() {
        return ToolSchemaDescriptor.builder(ToolNames.CANCEL_APPOINTMENT, "取消当前用户预约(高风险,需 appointmentId 由用户提供)")
                .param("appointmentId", ToolSchemaDescriptor.FieldSchema.string("预约 ID,必填,必须来自上一轮 getUserAppointments 的结果")
                        .asRequired())
                .param("reason", ToolSchemaDescriptor.FieldSchema.string("取消原因"))
                .build();
    }

    private static ToolSchemaDescriptor searchKnowledge() {
        return ToolSchemaDescriptor.builder(ToolNames.SEARCH_KNOWLEDGE, "检索已发布知识库")
                .param("query", ToolSchemaDescriptor.FieldSchema.string("检索词,空时使用用户当前消息"))
                .build();
    }
}
