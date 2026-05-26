package com.medora.agent.application;

import com.medora.agent.support.ContextPruner;
import com.medora.agent.support.TokenBudgetPlanner;
import com.medora.agent.tool.ToolExecutionResult;
import com.medora.agent.tool.ToolNames;
import com.medora.appointment.api.response.AppointmentCancelResponse;
import com.medora.appointment.api.response.AppointmentItem;
import com.medora.appointment.api.response.AppointmentResponse;
import com.medora.common.api.PageResponse;
import com.medora.knowledge.service.KnowledgeJsonService;
import com.medora.knowledge.service.RetrievalResult;
import com.medora.masterdata.api.response.DepartmentItem;
import com.medora.masterdata.api.response.DoctorItem;
import com.medora.masterdata.api.response.ScheduleSlotItem;
import com.medora.memory.service.ConversationSummaryService;
import com.medora.memory.service.MemoryService;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class PromptBuilder {

    private final TokenBudgetPlanner tokenBudgetPlanner;
    private final ContextPruner contextPruner;
    private final KnowledgeJsonService knowledgeJsonService;

    public PromptBuilder(TokenBudgetPlanner tokenBudgetPlanner,
                         ContextPruner contextPruner,
                         KnowledgeJsonService knowledgeJsonService) {
        this.tokenBudgetPlanner = tokenBudgetPlanner;
        this.contextPruner = contextPruner;
        this.knowledgeJsonService = knowledgeJsonService;
    }

    public PromptPackage buildPromptPackage(String userMessage,
                                            IntentDecision intent,
                                            ConversationSummaryService.ConversationContextSnapshot conversationContext,
                                            List<MemoryService.MemorySnippet> relevantMemories,
                                            List<RetrievalResult> retrievalResults,
                                            List<ToolExecutionResult> toolResults) {
        TokenBudgetPlanner.PromptBudgetPlan budgetPlan = tokenBudgetPlanner.plan(
                userMessage,
                conversationContext,
                relevantMemories,
                retrievalResults,
                toolResults
        );
        List<ConversationSummaryService.RecentMessage> prunedRecentMessages = contextPruner.pruneRecentMessages(
                conversationContext.recentMessages(),
                budgetPlan.recentMessageTokenCap()
        );
        List<MemoryService.MemorySnippet> prunedMemories = contextPruner.pruneMemories(
                relevantMemories,
                budgetPlan.memoryTokenCap()
        );
        List<RetrievalResult> prunedRetrievalResults = contextPruner.pruneRetrieval(
                retrievalResults,
                budgetPlan.retrievalTokenCap()
        );

        LinkedHashMap<String, Object> promptPayload = new LinkedHashMap<>();
        promptPayload.put("userMessage", safe(userMessage));
        promptPayload.put("sceneType", conversationContext.sceneType());
        promptPayload.put("intent", intent);
        promptPayload.put("conversationSummary", safe(conversationContext.summaryText()));
        promptPayload.put("conversationSummaryData", conversationContext.summaryData());
        promptPayload.put("businessState", conversationContext.businessState());
        promptPayload.put("longTermMemory", prunedMemories.stream().map(this::toMemoryPromptItem).toList());
        promptPayload.put("recentMessages", prunedRecentMessages.stream()
                .map(message -> Map.of(
                        "role", message.role(),
                        "content", message.content()
                ))
                .toList());
        promptPayload.put("retrievalResults", prunedRetrievalResults);
        promptPayload.put("toolResults", toolResults);
        List<String> contextNotes = new java.util.ArrayList<>(List.of(
                "会话摘要和长期记忆只作上下文提示，与当前用户输入冲突时以当前输入为准。",
                "业务事实以工具结果和知识引用为准，不得用长期记忆替代实时结果。"
        ));
        if (retrievalResults.isEmpty() && intent.needRetrieval()) {
            contextNotes.add("当前知识库未检索到与用户问题相关的内容。回答时应明确说明这一点，不要编造规则或流程。可以建议用户咨询医院导诊台。");
        }
        // 动态安全策略注入
        if (intent.intentType() != null) {
            String it = intent.intentType();
            if (it.contains("BOOK_APPOINTMENT") || it.contains("bookAppointment")) {
                contextNotes.add("当前处于预约操作阶段，必须展示完整预约信息后才能确认。");
            }
            if (it.contains("CANCEL_APPOINTMENT") || it.contains("cancelAppointment")) {
                contextNotes.add("当前处于取消操作阶段，必须展示取消后的不可逆提醒。");
            }
            if (it.contains("QUERY_DEPARTMENTS") || it.contains("MEDICAL")) {
                contextNotes.add("当前用户描述了医疗症状，只能建议科室，不做诊断。");
            }
        }
        promptPayload.put("contextNotes", contextNotes);

        String modelUserPrompt = knowledgeJsonService.toJson(promptPayload);
        int estimatedAfterPrune = tokenBudgetPlanner.estimateTextTokens(modelUserPrompt);

        LinkedHashMap<String, Object> tokenUsage = new LinkedHashMap<>();
        tokenUsage.put("inputTokens", 0);
        tokenUsage.put("outputTokens", 0);
        tokenUsage.put("estimatedPromptTokensBeforePrune", budgetPlan.estimatedBeforePrune());
        tokenUsage.put("estimatedPromptTokensAfterPrune", estimatedAfterPrune);
        tokenUsage.put("promptTokenLimit", budgetPlan.promptTokenLimit());
        tokenUsage.put("completionReserve", budgetPlan.completionReserve());
        tokenUsage.put("includedRecentMessages", prunedRecentMessages.size());
        tokenUsage.put("includedMemoryItems", prunedMemories.size());
        tokenUsage.put("includedRetrievalItems", prunedRetrievalResults.size());
        tokenUsage.put("truncatedRecentMessages", prunedRecentMessages.size() < conversationContext.recentMessages().size());
        tokenUsage.put("truncatedMemories", prunedMemories.size() < relevantMemories.size());
        tokenUsage.put("truncatedRetrievalResults", prunedRetrievalResults.size() < retrievalResults.size());

        return new PromptPackage(
                modelUserPrompt,
                buildFallbackReply(userMessage, conversationContext, prunedMemories, prunedRetrievalResults, toolResults),
                knowledgeJsonService.toJson(tokenUsage)
        );
    }

    private String buildFallbackReply(String userMessage,
                                      ConversationSummaryService.ConversationContextSnapshot conversationContext,
                                      List<MemoryService.MemorySnippet> relevantMemories,
                                      List<RetrievalResult> retrievalResults,
                                      List<ToolExecutionResult> toolResults) {
        if (!toolResults.isEmpty()) {
            return buildToolReply(toolResults);
        }
        if (!retrievalResults.isEmpty()) {
            return buildRetrievalReply(retrievalResults);
        }
        return buildGeneralReply(userMessage, conversationContext, relevantMemories);
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

    private String buildGeneralReply(String userMessage,
                                     ConversationSummaryService.ConversationContextSnapshot conversationContext,
                                     List<MemoryService.MemorySnippet> relevantMemories) {
        String normalized = userMessage == null ? "" : userMessage.trim();
        if (normalized.length() > 80) {
            normalized = normalized.substring(0, 80) + "...";
        }
        StringBuilder builder = new StringBuilder("已收到你的问题：“").append(normalized).append("”。");
        String memoryHint = relevantMemories.stream()
                .map(MemoryService.MemorySnippet::summary)
                .filter(StringUtils::hasText)
                .distinct()
                .limit(2)
                .reduce((left, right) -> left + "；" + right)
                .orElse("");
        if (StringUtils.hasText(memoryHint)) {
            builder.append("我记得").append(memoryHint).append("。");
        }
        if (StringUtils.hasText(conversationContext.summaryText())) {
            builder.append("当前会话摘要：")
                    .append(truncate(conversationContext.summaryText(), 96))
                    .append("。");
        }
        builder.append("你可以继续问我挂号规则、查询号源、预约或取消挂号。");
        return builder.toString();
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private Map<String, Object> toMemoryPromptItem(MemoryService.MemorySnippet memory) {
        return Map.of(
                "memoryType", memory.memoryType(),
                "memoryKey", memory.memoryKey(),
                "summary", memory.summary(),
                "confidence", memory.confidence(),
                "source", memory.source(),
                "value", memory.value()
        );
    }

    private String truncate(String value, int maxLength) {
        if (!StringUtils.hasText(value) || value.length() <= maxLength) {
            return value == null ? "" : value;
        }
        return value.substring(0, maxLength) + "...";
    }

    public record PromptPackage(
            String modelUserPrompt,
            String fallbackReply,
            String tokenUsageJson
    ) {
    }
}
