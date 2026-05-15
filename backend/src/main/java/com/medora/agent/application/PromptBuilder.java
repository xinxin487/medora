package com.medora.agent.application;

import com.medora.agent.tool.ToolExecutionResult;
import com.medora.agent.tool.ToolNames;
import com.medora.appointment.api.response.AppointmentCancelResponse;
import com.medora.appointment.api.response.AppointmentItem;
import com.medora.appointment.api.response.AppointmentResponse;
import com.medora.common.api.PageResponse;
import com.medora.knowledge.service.RetrievalResult;
import com.medora.masterdata.api.response.DepartmentItem;
import com.medora.masterdata.api.response.DoctorItem;
import com.medora.masterdata.api.response.ScheduleSlotItem;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PromptBuilder {

    public String build(String userMessage,
                        IntentDecision intent,
                        List<RetrievalResult> retrievalResults,
                        List<ToolExecutionResult> toolResults) {
        if (!toolResults.isEmpty()) {
            return buildToolReply(toolResults);
        }
        if (!retrievalResults.isEmpty()) {
            return buildRetrievalReply(retrievalResults);
        }
        return buildGeneralReply(userMessage);
    }

    private String buildToolReply(List<ToolExecutionResult> toolResults) {
        StringBuilder builder = new StringBuilder();
        for (ToolExecutionResult result : toolResults) {
            if (!result.success()) {
                builder.append(buildToolFailureReply(result));
                continue;
            }
            builder.append(buildToolSuccessReply(result));
        }
        return builder.toString().trim();
    }

    private String buildToolSuccessReply(ToolExecutionResult result) {
        if (ToolNames.QUERY_SCHEDULE_SLOTS.equals(result.toolName())) {
            return formatPageResult(result, "可预约号源", item -> {
                ScheduleSlotItem slot = (ScheduleSlotItem) item;
                return "- " + slot.scheduleDate() + " " + slot.periodCode()
                        + "，" + slot.department().name()
                        + "，" + slot.doctor().name()
                        + "，剩余 " + slot.availableQuota()
                        + "，slotId=" + slot.slotId();
            });
        }
        if (ToolNames.GET_USER_APPOINTMENTS.equals(result.toolName())) {
            String body = formatPageResult(result, "你的预约记录", item -> {
                AppointmentItem appointment = (AppointmentItem) item;
                return "- appointmentId=" + appointment.appointmentId()
                        + "，" + appointment.status()
                        + "，" + appointment.scheduleDate()
                        + " " + appointment.periodCode()
                        + "，" + appointment.departmentName()
                        + "，" + appointment.doctorName();
            });
            return body + "\n如需取消，请回复要取消的 `appointmentId`。";
        }
        if (ToolNames.QUERY_DEPARTMENTS.equals(result.toolName())) {
            return formatPageResult(result, "可参考科室", item -> {
                DepartmentItem department = (DepartmentItem) item;
                return "- " + department.name()
                        + "，departmentId=" + department.departmentId()
                        + "，" + nullToEmpty(department.description());
            });
        }
        if (ToolNames.QUERY_DOCTORS.equals(result.toolName())) {
            return formatPageResult(result, "可参考医生", item -> {
                DoctorItem doctor = (DoctorItem) item;
                return "- " + doctor.name()
                        + "，" + doctor.title()
                        + "，" + doctor.department().name()
                        + "，doctorId=" + doctor.doctorId();
            });
        }
        if (ToolNames.BOOK_APPOINTMENT.equals(result.toolName())
                || ToolNames.CANCEL_APPOINTMENT.equals(result.toolName())) {
            return formatAppointmentMutation(result);
        }
        return formatPageResult(result, result.summary(), Object::toString);
    }

    private String formatAppointmentMutation(ToolExecutionResult result) {
        if (result.data() instanceof AppointmentResponse appointment) {
            return result.summary()
                    + "\n- appointmentId=" + appointment.appointmentId()
                    + "\n- appointmentNo=" + appointment.appointmentNo()
                    + "\n- status=" + appointment.status()
                    + "\n- schedule=" + appointment.schedule().scheduleDate() + " " + appointment.schedule().periodCode()
                    + "\n- department=" + appointment.department().name()
                    + "\n- doctor=" + appointment.doctor().name();
        }
        if (result.data() instanceof AppointmentCancelResponse cancellation) {
            return result.summary()
                    + "\n- appointmentId=" + cancellation.appointmentId()
                    + "\n- status=" + cancellation.status()
                    + "\n- cancelledAt=" + cancellation.cancelledAt();
        }
        return result.summary();
    }

    private String formatPageResult(ToolExecutionResult result,
                                    String title,
                                    java.util.function.Function<Object, String> formatter) {
        if (!(result.data() instanceof PageResponse<?> page) || page.list().isEmpty()) {
            return title + "暂无可展示结果。";
        }
        StringBuilder builder = new StringBuilder(title).append("：\n");
        for (Object item : page.list()) {
            builder.append(formatter.apply(item)).append("\n");
        }
        return builder.toString();
    }

    private String buildToolFailureReply(ToolExecutionResult result) {
        if ("AGENT_TOOL_ARGUMENT_MISSING".equals(result.errorCode())) {
            return result.errorMessage() + "，请补充后我再继续办理。\n";
        }
        return "工具调用失败：" + result.errorMessage() + "。这次不会伪造业务结果，请稍后重试或换个说法。\n";
    }

    private String buildRetrievalReply(List<RetrievalResult> retrievalResults) {
        StringBuilder builder = new StringBuilder("基于已发布知识库，找到以下参考信息：");
        for (int index = 0; index < retrievalResults.size(); index++) {
            RetrievalResult result = retrievalResults.get(index);
            builder.append("\n\n[").append(index + 1).append("] ").append(result.snippet());
        }
        builder.append("\n\n以上内容来自知识库引用，正式医疗判断仍建议结合医生面诊。");
        return builder.toString();
    }

    private String buildGeneralReply(String userMessage) {
        String normalized = userMessage == null ? "" : userMessage.trim();
        if (normalized.length() > 80) {
            normalized = normalized.substring(0, 80) + "...";
        }
        return "已收到你的问题：“" + normalized
                + "”。你可以直接问我挂号规则、查询号源、预约或取消挂号。";
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
