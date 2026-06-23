package com.medora.adk.guard;

import com.google.adk.agents.Callbacks.BeforeToolCallbackSync;
import com.google.adk.agents.InvocationContext;
import com.google.adk.tools.BaseTool;
import com.google.adk.tools.ToolContext;
import com.medora.agent.tool.ToolNames;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * BeforeToolCallback —— 高风险工具参数校验,挂在 BookingAgent。
 *
 * 设计原则:不调 LLM。这是「格式校验层」(LayerPolicy):
 *  - bookAppointment 要求 slotId 必须存在且非空(LLM 必填 schema 已声明,但 LLM 可能幻觉空值)
 *  - cancelAppointment 要求 appointmentId 必须存在且非空
 *
 * 拒绝时:不修改 args,而是直接抛 BusinessException(被 MedoraFunctionTool 捕获并序列化
 * 为 FAILED 结构)。LLM 收到 FAILED 后会自行反问用户(由 BookingAgent 的 instruction 引导)。
 *
 * 不做 LLM 二次核验:每次预约多一次 LLM 调用得不偿失。slotId/appointmentId 是确定性 ID,
 * schema 校验已经足够。
 */
@Component
public class HighRiskToolBeforeCallback implements BeforeToolCallbackSync {

    private static final Logger log = LoggerFactory.getLogger(HighRiskToolBeforeCallback.class);

    @Override
    public Optional<Map<String, Object>> call(InvocationContext invocationContext,
                                              BaseTool tool,
                                              Map<String, Object> args,
                                              ToolContext toolContext) {
        Objects.requireNonNull(tool, "tool");
        String toolName = tool.name();
        if (ToolNames.BOOK_APPOINTMENT.equals(toolName)) {
            return validateBookAppointment(args, toolContext);
        }
        if (ToolNames.CANCEL_APPOINTMENT.equals(toolName)) {
            return validateCancelAppointment(args, toolContext);
        }
        return Optional.empty();
    }

    private static Optional<Map<String, Object>> validateBookAppointment(Map<String, Object> args, ToolContext ctx) {
        Object slotId = args == null ? null : args.get("slotId");
        if (!(slotId instanceof String s) || s.isBlank()) {
            log.warn("HighRiskToolBeforeCallback blocked bookAppointment: missing slotId");
            throw new com.medora.common.exception.BusinessException(
                    "AGENT_TOOL_ARGUMENT_MISSING",
                    org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY,
                    "预约需要明确 slotId,请让用户提供具体的号源 ID");
        }
        return Optional.empty();
    }

    private static Optional<Map<String, Object>> validateCancelAppointment(Map<String, Object> args, ToolContext ctx) {
        Object appointmentId = args == null ? null : args.get("appointmentId");
        if (!(appointmentId instanceof String s) || s.isBlank()) {
            log.warn("HighRiskToolBeforeCallback blocked cancelAppointment: missing appointmentId");
            throw new com.medora.common.exception.BusinessException(
                    "AGENT_TOOL_ARGUMENT_MISSING",
                    org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY,
                    "取消预约需要明确 appointmentId,请让用户提供具体的预约 ID");
        }
        return Optional.empty();
    }
}
