package com.medora.memory.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medora.agent.application.AgentModelGateway;
import com.medora.agent.application.AgentRunResult;
import com.medora.agent.context.ContextFingerprint;
import com.medora.agent.tool.ToolExecutionResult;
import com.medora.agent.tool.ToolNames;
import com.medora.appointment.api.response.AppointmentCancelResponse;
import com.medora.appointment.api.response.AppointmentItem;
import com.medora.appointment.api.response.AppointmentResponse;
import com.medora.chat.cache.RedisContextCacheService;
import com.medora.chat.domain.ChatMessageEntity;
import com.medora.chat.domain.ConversationEntity;
import com.medora.chat.repository.ChatMessageRepository;
import com.medora.chat.repository.ConversationRepository;
import com.medora.common.api.PageResponse;
import com.medora.common.exception.BusinessException;
import com.medora.knowledge.service.KnowledgeJsonService;
import com.medora.masterdata.api.response.ScheduleSlotItem;
import com.medora.memory.domain.ConversationSummaryEntity;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ConversationSummaryService {

    private static final Pattern EXPLICIT_ALLERGY = Pattern.compile("(?:我|本人|患者)对([\\p{IsHan}A-Za-z0-9]{1,20})过敏");

    private final ConversationRepository conversationRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ConversationSummaryRepository conversationSummaryRepository;
    private final KnowledgeJsonService knowledgeJsonService;
    private final ObjectMapper objectMapper;
    private final RedisContextCacheService redisContextCacheService;
    private final AgentModelGateway agentModelGateway;

    public ConversationSummaryService(ConversationRepository conversationRepository,
                                      ChatMessageRepository chatMessageRepository,
                                      ConversationSummaryRepository conversationSummaryRepository,
                                      KnowledgeJsonService knowledgeJsonService,
                                      ObjectMapper objectMapper,
                                      RedisContextCacheService redisContextCacheService,
                                      AgentModelGateway agentModelGateway) {
        this.conversationRepository = conversationRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.conversationSummaryRepository = conversationSummaryRepository;
        this.knowledgeJsonService = knowledgeJsonService;
        this.objectMapper = objectMapper;
        this.redisContextCacheService = redisContextCacheService;
        this.agentModelGateway = agentModelGateway;
    }

    @Transactional(readOnly = true)
    public ConversationContextSnapshot loadContext(Long conversationId) {
        ConversationEntity conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new BusinessException("CHAT_CONVERSATION_NOT_FOUND", HttpStatus.NOT_FOUND,
                        "conversation not found"));
        ConversationSummaryEntity summary = conversationSummaryRepository.findByConversation(conversation).orElse(null);
        return new ConversationContextSnapshot(
                conversation.getSceneType(),
                summary == null ? "" : summary.getSummaryText(),
                summary == null ? Map.of() : readJsonObject(summary.getSummaryJson()),
                summary == null ? Map.of() : readJsonObject(summary.getBusinessStateJson()),
                loadRecentMessages(conversation)
        );
    }

    @Transactional
    public void refreshAfterAssistantReply(ConversationEntity conversation,
                                           String userMessage,
                                           String assistantReply,
                                           AgentRunResult runResult) {
        long messageCount = chatMessageRepository.countByConversation(conversation);
        ConversationSummaryEntity entity = conversationSummaryRepository.findByConversation(conversation)
                .orElseGet(() -> ConversationSummaryEntity.create(conversation));
        Map<String, Object> businessState = buildBusinessState(conversation, userMessage, assistantReply, runResult);
        Map<String, Object> summaryData = buildSummaryData(userMessage, runResult);
        Map<String, Object> previousSummary = readJsonObject(entity.getSummaryJson());
        List<String> newFacts = castStringList(summaryData.get("confirmedFacts"));
        List<String> priorFacts = castStringList(previousSummary.get("confirmedFacts"));
        String winningPeriodKey = java.util.stream.Stream.concat(priorFacts.stream(), newFacts.stream())
                .map(ContextFingerprint::periodPreferenceKey).filter(StringUtils::hasText)
                .reduce((first, latest) -> latest).orElse("");
        LinkedHashSet<String> confirmedFacts = new LinkedHashSet<>();
        for (String priorFact : priorFacts) {
            String priorKey = ContextFingerprint.periodPreferenceKey(priorFact);
            if (!priorKey.isEmpty() && !priorKey.equals(winningPeriodKey)) continue;
            confirmedFacts.add(priorFact);
        }
        for (String fact : newFacts) {
            String key = ContextFingerprint.periodPreferenceKey(fact);
            if (key.isEmpty() || key.equals(winningPeriodKey)) confirmedFacts.add(fact);
        }
        List<String> orderedFacts = confirmedFacts.stream().filter(StringUtils::hasText)
                .map(fact -> truncate(fact, 100)).toList();
        List<String> boundedFacts = new ArrayList<>();
        if (orderedFacts.size() > 12) {
            boundedFacts.addAll(orderedFacts.subList(0, 4));
            boundedFacts.addAll(orderedFacts.subList(orderedFacts.size() - 8, orderedFacts.size()));
        } else {
            boundedFacts.addAll(orderedFacts);
        }
        summaryData.put("confirmedFacts", boundedFacts);
        String summaryText = buildSummaryText(summaryData);
        entity.refresh(
                summaryText,
                knowledgeJsonService.toJson(summaryData),
                knowledgeJsonService.toJson(businessState),
                (int) Math.max(0, messageCount),
                OffsetDateTime.now()
        );
        conversationSummaryRepository.save(entity);
    }

    private Map<String, Object> buildSummaryData(String userMessage, AgentRunResult runResult) {
        LinkedHashMap<String, Object> summaryData = new LinkedHashMap<>();
        summaryData.put("topic", resolveTopic(runResult.intentDecision().intentType(), runResult.toolResults(), userMessage));
        summaryData.put("confirmedFacts", collectConfirmedFacts(userMessage, runResult.toolResults()));
        summaryData.put("pendingTasks", collectPendingTasks(runResult.toolResults()));
        summaryData.put("toolHighlights", collectToolHighlights(runResult.toolResults()));
        return summaryData;
    }

    private Map<String, Object> buildBusinessState(ConversationEntity conversation,
                                                   String userMessage,
                                                   String assistantReply,
                                                   AgentRunResult runResult) {
        LinkedHashMap<String, Object> businessState = new LinkedHashMap<>();
        businessState.put("sceneType", conversation.getSceneType());
        businessState.put("lastIntent", runResult.intentDecision().intentType());
        businessState.put("routeMode", runResult.intentDecision().routeMode().name());
        businessState.put("lastUserMessage", truncate(userMessage, 160));
        businessState.put("lastAssistantReply", truncate(assistantReply, 220));
        businessState.put("recentMessageCount", chatMessageRepository.countByConversation(conversation));
        applyToolState(businessState, runResult.toolResults());
        return businessState;
    }

    private void applyToolState(Map<String, Object> businessState, List<ToolExecutionResult> toolResults) {
        if (toolResults == null || toolResults.isEmpty()) {
            businessState.put("lastToolStatus", "NOT_USED");
            return;
        }
        ToolExecutionResult last = toolResults.get(toolResults.size() - 1);
        businessState.put("lastToolName", last.toolName());
        businessState.put("lastToolStatus", last.status());
        if (!last.success()) {
            businessState.put("lastToolError", last.errorMessage());
            return;
        }
        if (last.data() instanceof AppointmentResponse appointment) {
            businessState.put("lastAppointmentId", appointment.appointmentId());
            businessState.put("lastSlotId", appointment.schedule().slotId());
            businessState.put("lastScheduleDate", appointment.schedule().scheduleDate());
            businessState.put("lastPeriodCode", appointment.schedule().periodCode());
            businessState.put("lastDepartmentName", appointment.department().name());
            businessState.put("lastDoctorName", appointment.doctor().name());
            return;
        }
        if (last.data() instanceof AppointmentCancelResponse cancellation) {
            businessState.put("lastAppointmentId", cancellation.appointmentId());
            businessState.put("lastAppointmentStatus", cancellation.status());
            businessState.put("lastCancelledAt", cancellation.cancelledAt());
            return;
        }
        if (ToolNames.QUERY_SCHEDULE_SLOTS.equals(last.toolName()) && last.data() instanceof PageResponse<?> page
                && !page.list().isEmpty() && page.list().getFirst() instanceof ScheduleSlotItem slot) {
            businessState.put("lastSlotId", slot.slotId());
            businessState.put("lastScheduleDate", slot.scheduleDate());
            businessState.put("lastPeriodCode", slot.periodCode());
            businessState.put("lastDepartmentName", slot.department().name());
            businessState.put("lastDoctorName", slot.doctor().name());
            return;
        }
        if (ToolNames.GET_USER_APPOINTMENTS.equals(last.toolName()) && last.data() instanceof PageResponse<?> page
                && !page.list().isEmpty() && page.list().getFirst() instanceof AppointmentItem appointmentItem) {
            businessState.put("lastAppointmentId", appointmentItem.appointmentId());
            businessState.put("lastAppointmentStatus", appointmentItem.status());
            businessState.put("lastScheduleDate", appointmentItem.scheduleDate());
            businessState.put("lastPeriodCode", appointmentItem.periodCode());
            businessState.put("lastDepartmentName", appointmentItem.departmentName());
            businessState.put("lastDoctorName", appointmentItem.doctorName());
        }
    }

    /**
     * 收集确认事实 — LLM 优先，规则降级。
     */
    private List<String> collectConfirmedFacts(String userMessage, List<ToolExecutionResult> toolResults) {
        LinkedHashSet<String> facts = new LinkedHashSet<>();

        if (userMessage != null) {
            Matcher allergy = EXPLICIT_ALLERGY.matcher(userMessage);
            if (allergy.find()) {
                String following = userMessage.substring(allergy.end()).stripLeading();
                String preceding = userMessage.substring(0, allergy.start());
                boolean isQuestion = following.startsWith("吗") || following.startsWith("?")
                        || following.startsWith("？") || following.startsWith("会")
                        || following.startsWith("该") || following.startsWith("怎么办")
                        || preceding.contains("是不是") || preceding.contains("是否")
                        || preceding.contains("请问") || preceding.contains("如果")
                        || preceding.contains("假如") || preceding.contains("假设")
                        || preceding.contains("万一");
                if (!isQuestion) {
                    facts.add("用户自述对" + allergy.group(1) + "过敏");
                }
            }
        }

        // 从工具结果提取事实（确定性高，不需要 LLM）
        for (ToolExecutionResult result : toolResults) {
            if (!result.success()) {
                continue;
            }
            if (ToolNames.QUERY_SCHEDULE_SLOTS.equals(result.toolName())
                    && result.data() instanceof PageResponse<?> page
                    && !page.list().isEmpty()
                    && page.list().getFirst() instanceof ScheduleSlotItem slot) {
                facts.add("最近查询过" + slot.scheduleDate() + slot.periodCode()
                        + slot.department().name() + slot.doctor().name() + "号源");
            }
            if (result.data() instanceof AppointmentResponse appointment) {
                facts.add("最近成功预约了" + appointment.schedule().scheduleDate()
                        + appointment.schedule().periodCode()
                        + appointment.department().name()
                        + appointment.doctor().name());
            }
            if (result.data() instanceof AppointmentCancelResponse cancellation) {
                facts.add("最近取消了预约 " + cancellation.appointmentId());
            }
        }

        // 从用户消息提取关注点 — 使用 LLM 语义理解
        try {
            String toolSummary = facts.isEmpty() ? "无" : String.join("；", facts);
            var llmResult = agentModelGateway.generateConversationSummary(userMessage, toolSummary);
            if (llmResult.isPresent()) {
                String content = llmResult.get();
                // 尝试提取 confirmedFacts 数组
                int arrayStart = content.indexOf("\"confirmedFacts\"");
                if (arrayStart >= 0) {
                    int bracketStart = content.indexOf("[", arrayStart);
                    int bracketEnd = content.indexOf("]", bracketStart);
                    if (bracketStart >= 0 && bracketEnd > bracketStart) {
                        String arrayContent = content.substring(bracketStart, bracketEnd + 1);
                        List<String> llmFacts = objectMapper.readValue(arrayContent,
                                new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {});
                        facts.addAll(llmFacts);
                    }
                }
            }
        } catch (Exception ex) {
            // LLM 不可用，回退到关键词
            if (containsAny(userMessage, "上午", "早上")) {
                facts.add("用户当前关注上午号源");
            }
            if (containsAny(userMessage, "下午", "晚上")) {
                facts.add("用户当前关注下午号源");
            }
        }

        return facts.stream().limit(4).toList();
    }

    private List<String> collectPendingTasks(List<ToolExecutionResult> toolResults) {
        LinkedHashSet<String> tasks = new LinkedHashSet<>();
        for (ToolExecutionResult result : toolResults) {
            if (!result.success() && "AGENT_TOOL_ARGUMENT_MISSING".equals(result.errorCode())) {
                tasks.add(result.errorMessage());
                continue;
            }
            if (ToolNames.QUERY_SCHEDULE_SLOTS.equals(result.toolName())
                    && result.success()
                    && result.data() instanceof PageResponse<?> page
                    && !page.list().isEmpty()) {
                tasks.add("等待用户提供 slotId 继续预约");
            }
            if (ToolNames.GET_USER_APPOINTMENTS.equals(result.toolName())
                    && result.success()
                    && result.data() instanceof PageResponse<?> page
                    && !page.list().isEmpty()) {
                tasks.add("等待用户提供 appointmentId 继续取消");
            }
        }
        return tasks.stream().limit(3).toList();
    }

    private List<String> collectToolHighlights(List<ToolExecutionResult> toolResults) {
        return toolResults.stream()
                .map(result -> result.success() ? result.summary() : result.errorMessage())
                .filter(StringUtils::hasText)
                .map(value -> truncate(value, 120))
                .distinct()
                .limit(4)
                .toList();
    }

    private String resolveTopic(String intentType,
                                List<ToolExecutionResult> toolResults,
                                String userMessage) {
        // 尝试 LLM 生成摘要
        try {
            String toolSummary = toolResults.stream()
                    .map(result -> result.success() ? result.summary() : result.errorMessage())
                    .filter(StringUtils::hasText)
                    .reduce((a, b) -> a + "; " + b)
                    .orElse("无工具调用");
            var llmSummary = agentModelGateway.generateConversationSummary(userMessage, toolSummary);
            if (llmSummary.isPresent()) {
                String content = llmSummary.get();
                // 提取 topic 字段
                int topicStart = content.indexOf("\"topic\"");
                if (topicStart >= 0) {
                    int valueStart = content.indexOf(":", topicStart) + 1;
                    int quoteStart = content.indexOf("\"", valueStart);
                    int quoteEnd = content.indexOf("\"", quoteStart + 1);
                    if (quoteStart >= 0 && quoteEnd > quoteStart) {
                        return content.substring(quoteStart + 1, quoteEnd);
                    }
                }
            }
        } catch (Exception ex) {
            // LLM 不可用，回退到规则
        }

        // 规则降级
        if (toolResults.stream().anyMatch(result -> ToolNames.PREPARE_APPOINTMENT.equals(result.toolName()))) {
            return "预约挂号办理";
        }
        if (toolResults.stream().anyMatch(result -> ToolNames.CANCEL_APPOINTMENT.equals(result.toolName()))) {
            return "取消挂号办理";
        }
        if (toolResults.stream().anyMatch(result -> ToolNames.QUERY_SCHEDULE_SLOTS.equals(result.toolName()))) {
            return "号源查询";
        }
        if (toolResults.stream().anyMatch(result -> ToolNames.QUERY_DOCTORS.equals(result.toolName()))) {
            return "医生查询";
        }
        if (toolResults.stream().anyMatch(result -> ToolNames.QUERY_DEPARTMENTS.equals(result.toolName()))) {
            return "科室导诊";
        }
        if (intentType != null && intentType.contains("KNOWLEDGE")) {
            return "挂号规则说明";
        }
        if (containsAny(userMessage, "挂号", "预约", "取消")) {
            return "医疗服务咨询";
        }
        return "一般咨询";
    }

    private String buildSummaryText(Map<String, Object> summaryData) {
        String topic = String.valueOf(summaryData.getOrDefault("topic", "一般咨询"));
        List<String> confirmedFacts = castStringList(summaryData.get("confirmedFacts"));
        List<String> pendingTasks = castStringList(summaryData.get("pendingTasks"));
        List<String> toolHighlights = castStringList(summaryData.get("toolHighlights"));

        StringBuilder builder = new StringBuilder("当前主题：").append(topic).append("。");
        if (!confirmedFacts.isEmpty()) {
            builder.append("已确认：").append(String.join("；", confirmedFacts)).append("。");
        }
        if (!pendingTasks.isEmpty()) {
            builder.append("待完成：").append(String.join("；", pendingTasks)).append("。");
        }
        if (!toolHighlights.isEmpty()) {
            builder.append("最近工具结果：").append(String.join("；", toolHighlights)).append("。");
        }
        return builder.toString();
    }

    private List<RecentMessage> loadRecentMessages(ConversationEntity conversation) {
        UUID conversationPublicId = conversation.getPublicId();
        if (conversationPublicId != null) {
            String publicId = conversationPublicId.toString();
            try {
                List<RecentMessage> cached = redisContextCacheService.getRecentMessages(publicId);
                if (cached != null && !cached.isEmpty()) {
                    return cached;
                }
            } catch (RuntimeException ignored) {
                // Redis is a cache; the durable chat messages remain the fallback.
            }
        }
        List<ChatMessageEntity> rows = chatMessageRepository.findTop12ByConversationOrderByCreatedAtDescIdDesc(conversation);
        List<RecentMessage> messages = new ArrayList<>();
        for (int index = rows.size() - 1; index >= 0; index--) {
            ChatMessageEntity row = rows.get(index);
            if (!StringUtils.hasText(row.getContent())) {
                continue;
            }
            RecentMessage msg = new RecentMessage(row.getRole(), truncate(row.getContent(), 280));
            messages.add(msg);
        }
        return messages;
    }

    private Map<String, Object> readJsonObject(String value) {
        if (!StringUtils.hasText(value)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(value, new TypeReference<Map<String, Object>>() {
            });
        } catch (IOException ex) {
            return Map.of();
        }
    }

    @SuppressWarnings("unchecked")
    private List<String> castStringList(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return List.of();
    }

    private boolean containsAny(String value, String... patterns) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        for (String pattern : patterns) {
            if (value.contains(pattern)) {
                return true;
            }
        }
        return false;
    }

    private String truncate(String value, int maxLength) {
        if (!StringUtils.hasText(value) || value.length() <= maxLength) {
            return value == null ? "" : value;
        }
        return value.substring(0, maxLength) + "...";
    }

    public record ConversationContextSnapshot(
            String sceneType,
            String summaryText,
            Map<String, Object> summaryData,
            Map<String, Object> businessState,
            List<RecentMessage> recentMessages
    ) {
    }

    public record RecentMessage(
            String role,
            String content
    ) {
    }
}
