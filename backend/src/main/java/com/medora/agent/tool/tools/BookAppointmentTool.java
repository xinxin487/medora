package com.medora.agent.tool.tools;

import com.medora.agent.tool.ToolArguments;
import com.medora.agent.tool.ToolDefinition;
import com.medora.agent.tool.ToolExecutionContext;
import com.medora.agent.tool.ToolExecutionResult;
import com.medora.agent.tool.ToolNames;
import com.medora.agent.tool.ToolRiskLevel;
import com.medora.appointment.api.request.CreateAppointmentRequest;
import com.medora.appointment.service.AppointmentCommandService;
import com.medora.common.exception.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class BookAppointmentTool implements ToolDefinition {

    private final AppointmentCommandService appointmentCommandService;

    public BookAppointmentTool(AppointmentCommandService appointmentCommandService) {
        this.appointmentCommandService = appointmentCommandService;
    }

    @Override
    public String name() {
        return ToolNames.BOOK_APPOINTMENT;
    }

    @Override
    public String description() {
        return "预约挂号";
    }

    @Override
    public ToolRiskLevel riskLevel() {
        return ToolRiskLevel.HIGH;
    }

    @Override
    public ToolExecutionResult execute(Map<String, Object> arguments, ToolExecutionContext context) {
        String slotId = ToolArguments.stringValue(arguments, "slotId");
        if (slotId == null) {
            throw new BusinessException("AGENT_TOOL_ARGUMENT_MISSING", HttpStatus.UNPROCESSABLE_ENTITY,
                    "预约需要明确 slotId");
        }
        String idempotencyKey = "AGT-" + compact(context.traceId(), 24) + "-" + compact(slotId, 12);
        var result = appointmentCommandService.createForUser(
                context.userAccountId(),
                new CreateAppointmentRequest(slotId, "Agent 对话预约"),
                idempotencyKey,
                "AGENT"
        );
        return ToolExecutionResult.success(name(), result, "预约已提交，预约号：" + result.appointmentNo() + "。");
    }

    private String compact(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        String compacted = value.replace("-", "");
        return compacted.length() <= maxLength ? compacted : compacted.substring(0, maxLength);
    }
}
