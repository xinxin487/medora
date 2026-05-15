package com.medora.agent.tool.tools;

import com.medora.agent.tool.ToolArguments;
import com.medora.agent.tool.ToolDefinition;
import com.medora.agent.tool.ToolExecutionContext;
import com.medora.agent.tool.ToolExecutionResult;
import com.medora.agent.tool.ToolNames;
import com.medora.appointment.api.query.AppointmentListQuery;
import com.medora.appointment.service.AppointmentQueryService;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class GetUserAppointmentsTool implements ToolDefinition {

    private final AppointmentQueryService appointmentQueryService;

    public GetUserAppointmentsTool(AppointmentQueryService appointmentQueryService) {
        this.appointmentQueryService = appointmentQueryService;
    }

    @Override
    public String name() {
        return ToolNames.GET_USER_APPOINTMENTS;
    }

    @Override
    public String description() {
        return "查询当前用户预约列表";
    }

    @Override
    public ToolExecutionResult execute(Map<String, Object> arguments, ToolExecutionContext context) {
        var result = appointmentQueryService.listMineForUser(
                context.userAccountId(),
                new AppointmentListQuery(
                        ToolArguments.stringValue(arguments, "status"),
                        ToolArguments.stringValue(arguments, "scheduleDateFrom"),
                        ToolArguments.stringValue(arguments, "scheduleDateTo"),
                        1,
                        ToolArguments.intValue(arguments, "pageSize", 5)
                )
        );
        return ToolExecutionResult.success(name(), result, "已查询到 " + result.list().size() + " 条预约记录。");
    }
}
