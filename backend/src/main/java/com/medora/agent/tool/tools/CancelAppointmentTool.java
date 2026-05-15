package com.medora.agent.tool.tools;

import com.medora.agent.tool.ToolArguments;
import com.medora.agent.tool.ToolDefinition;
import com.medora.agent.tool.ToolExecutionContext;
import com.medora.agent.tool.ToolExecutionResult;
import com.medora.agent.tool.ToolNames;
import com.medora.appointment.api.request.CancelAppointmentRequest;
import com.medora.appointment.service.AppointmentCommandService;
import com.medora.common.exception.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class CancelAppointmentTool implements ToolDefinition {

    private final AppointmentCommandService appointmentCommandService;

    public CancelAppointmentTool(AppointmentCommandService appointmentCommandService) {
        this.appointmentCommandService = appointmentCommandService;
    }

    @Override
    public String name() {
        return ToolNames.CANCEL_APPOINTMENT;
    }

    @Override
    public String description() {
        return "取消当前用户预约";
    }

    @Override
    public ToolExecutionResult execute(Map<String, Object> arguments, ToolExecutionContext context) {
        String appointmentId = ToolArguments.stringValue(arguments, "appointmentId");
        if (appointmentId == null) {
            throw new BusinessException("AGENT_TOOL_ARGUMENT_MISSING", HttpStatus.UNPROCESSABLE_ENTITY,
                    "取消预约需要明确 appointmentId");
        }
        String reason = ToolArguments.stringValue(arguments, "reason");
        var result = appointmentCommandService.cancelForUser(
                context.userAccountId(),
                appointmentId,
                new CancelAppointmentRequest(reason == null ? "Agent 对话取消" : reason),
                "AGENT"
        );
        return ToolExecutionResult.success(name(), result, "预约已取消。");
    }
}
