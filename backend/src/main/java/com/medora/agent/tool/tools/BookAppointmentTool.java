package com.medora.agent.tool.tools;

import com.medora.agent.tool.ToolArguments;
import com.medora.agent.tool.ToolDefinition;
import com.medora.agent.tool.ToolExecutionContext;
import com.medora.agent.tool.ToolExecutionResult;
import com.medora.agent.tool.ToolNames;
import com.medora.agent.tool.ToolRiskLevel;
import com.medora.a2ui.service.A2uiActionService;
import com.medora.a2ui.service.AppointmentSurfaceFactory;
import com.medora.appointment.hold.AppointmentHoldRepository;
import com.medora.appointment.hold.AppointmentHoldService;
import com.medora.common.exception.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class BookAppointmentTool implements ToolDefinition {

    private final AppointmentHoldService holds;
    private final AppointmentHoldRepository holdRepository;
    private final A2uiActionService actions;
    private final AppointmentSurfaceFactory surfaces;

    public BookAppointmentTool(AppointmentHoldService holds, AppointmentHoldRepository holdRepository,
                               A2uiActionService actions, AppointmentSurfaceFactory surfaces) {
        this.holds = holds; this.holdRepository = holdRepository; this.actions = actions; this.surfaces = surfaces;
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
        var prepared = holds.prepare(context.userAccountId(), slotId, idempotencyKey, "AGENT");
        var hold = holdRepository.findByUserIdAndIdempotencyKey(context.userAccountId(), idempotencyKey)
                .orElseThrow(() -> new IllegalStateException("prepared appointment hold is missing"));
        var action = actions.issue(hold, "appointment-" + hold.getPublicId(), java.util.UUID.fromString(context.conversationId()));
        var surface = surfaces.create(hold, action);
        return ToolExecutionResult.success(name(), java.util.Map.of("prepared", prepared, "surface", surface),
                "已生成预约确认卡片，请您确认后才会完成挂号。");
    }

    private String compact(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        String compacted = value.replace("-", "");
        return compacted.length() <= maxLength ? compacted : compacted.substring(0, maxLength);
    }
}
