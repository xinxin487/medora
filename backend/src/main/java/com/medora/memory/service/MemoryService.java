package com.medora.memory.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medora.agent.application.AgentRunResult;
import com.medora.agent.tool.ToolExecutionResult;
import com.medora.appointment.api.response.AppointmentResponse;
import com.medora.auth.domain.UserAccountEntity;
import com.medora.knowledge.service.KnowledgeJsonService;
import com.medora.memory.domain.UserMemoryProfileEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class MemoryService {

    static final String MEMORY_TYPE_PREFERRED_DEPARTMENT = "PREFERRED_DEPARTMENT";
    static final String MEMORY_TYPE_PREFERRED_DOCTOR = "PREFERRED_DOCTOR";
    static final String MEMORY_TYPE_PREFERRED_PERIOD = "PREFERRED_PERIOD";
    static final String MEMORY_TYPE_LAST_SUCCESSFUL_APPOINTMENT = "LAST_SUCCESSFUL_APPOINTMENT";
    private static final int DEFAULT_MEMORY_TTL_DAYS = 180;

    private final UserMemoryProfileRepository userMemoryProfileRepository;
    private final KnowledgeJsonService knowledgeJsonService;
    private final ObjectMapper objectMapper;

    public MemoryService(UserMemoryProfileRepository userMemoryProfileRepository,
                         KnowledgeJsonService knowledgeJsonService,
                         ObjectMapper objectMapper) {
        this.userMemoryProfileRepository = userMemoryProfileRepository;
        this.knowledgeJsonService = knowledgeJsonService;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<MemorySnippet> loadRelevantMemory(Long userAccountId, String intentType) {
        List<String> memoryTypes = relevantTypes(intentType);
        if (memoryTypes.isEmpty()) {
            return List.of();
        }
        return userMemoryProfileRepository
                .findByUserAccountIdAndStatusAndMemoryTypeInOrderByConfidenceDescUpdatedAtDesc(
                        userAccountId,
                        "ACTIVE",
                        memoryTypes
                )
                .stream()
                .filter(entity -> entity.getExpiresAt() == null || entity.getExpiresAt().isAfter(OffsetDateTime.now()))
                .map(entity -> new MemorySnippet(
                        entity.getMemoryType(),
                        entity.getMemoryKey(),
                        entity.getSummary(),
                        readJsonObject(entity.getValueJson()),
                        entity.getConfidence().doubleValue(),
                        entity.getSource()
                ))
                .toList();
    }

    @Transactional
    public void captureFromAgentRun(UserAccountEntity userAccount,
                                    String userMessage,
                                    AgentRunResult runResult) {
        captureFromUserMessage(userAccount, userMessage);
        for (ToolExecutionResult result : runResult.toolResults()) {
            if (!result.success()) {
                continue;
            }
            if (result.data() instanceof AppointmentResponse appointment) {
                writeAppointmentMemories(userAccount, appointment);
            } else if ("queryScheduleSlots".equals(result.toolName())) {
                captureScheduleQuerySignal(userAccount, result);
            }
        }
    }

    private void writeAppointmentMemories(UserAccountEntity userAccount, AppointmentResponse appointment) {
        upsertMemory(
                userAccount,
                MEMORY_TYPE_PREFERRED_DEPARTMENT,
                appointment.department().departmentId(),
                "你最近成功预约过" + appointment.department().name(),
                Map.of(
                        "departmentId", appointment.department().departmentId(),
                        "departmentName", appointment.department().name(),
                        "sourceAppointmentId", appointment.appointmentId()
                ),
                1.0,
                "BOOK_APPOINTMENT",
                null
        );
        upsertMemory(
                userAccount,
                MEMORY_TYPE_PREFERRED_DOCTOR,
                appointment.doctor().doctorId(),
                "你最近成功预约过" + appointment.doctor().name(),
                Map.of(
                        "doctorId", appointment.doctor().doctorId(),
                        "doctorName", appointment.doctor().name(),
                        "departmentName", appointment.department().name(),
                        "sourceAppointmentId", appointment.appointmentId()
                ),
                1.0,
                "BOOK_APPOINTMENT",
                null
        );
        upsertMemory(
                userAccount,
                MEMORY_TYPE_PREFERRED_PERIOD,
                appointment.schedule().periodCode(),
                "你最近成功预约过" + humanizePeriod(appointment.schedule().periodCode()) + "号源",
                Map.of(
                        "periodCode", appointment.schedule().periodCode(),
                        "scheduleDate", appointment.schedule().scheduleDate(),
                        "sourceAppointmentId", appointment.appointmentId()
                ),
                1.0,
                "BOOK_APPOINTMENT",
                null
        );
        upsertMemory(
                userAccount,
                MEMORY_TYPE_LAST_SUCCESSFUL_APPOINTMENT,
                "LATEST",
                "最近一次成功预约：" + appointment.schedule().scheduleDate()
                        + appointment.schedule().periodCode()
                        + appointment.department().name()
                        + appointment.doctor().name(),
                Map.of(
                        "appointmentId", appointment.appointmentId(),
                        "appointmentNo", appointment.appointmentNo(),
                        "slotId", appointment.schedule().slotId(),
                        "scheduleDate", appointment.schedule().scheduleDate(),
                        "periodCode", appointment.schedule().periodCode(),
                        "departmentId", appointment.department().departmentId(),
                        "departmentName", appointment.department().name(),
                        "doctorId", appointment.doctor().doctorId(),
                        "doctorName", appointment.doctor().name()
                ),
                1.0,
                "BOOK_APPOINTMENT",
                null
        );
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void captureScheduleQuerySignal(UserAccountEntity userAccount, ToolExecutionResult result) {
        if (!(result.data() instanceof com.medora.common.api.PageResponse page)) {
            return;
        }
        java.util.List<?> items = page.list();
        if (items == null || items.isEmpty()) {
            return;
        }
        Object first = items.get(0);
        if (first instanceof com.medora.masterdata.api.response.ScheduleSlotItem slot) {
            String deptId = slot.department().departmentId();
            if (StringUtils.hasText(deptId)) {
                upsertMemory(
                        userAccount,
                        MEMORY_TYPE_PREFERRED_DEPARTMENT,
                        deptId,
                        "用户查询过" + slot.department().name() + "号源",
                        Map.of("departmentId", deptId, "departmentName", slot.department().name(), "source", "TOOL_QUERY"),
                        computeConfidence("TOOL_QUERY"),
                        "TOOL_QUERY",
                        null
                );
            }
        }
    }

    private void captureFromUserMessage(UserAccountEntity userAccount, String userMessage) {
        if (!StringUtils.hasText(userMessage)) {
            return;
        }
        // 时段偏好 — 扩展模式匹配
        if (containsAny(userMessage, "我习惯上午", "我偏好上午", "优先上午", "尽量上午",
                "上午的号", "挂上午", "约上午", "上午方便", "上午有空", "上午可以")) {
            upsertMemory(
                    userAccount,
                    MEMORY_TYPE_PREFERRED_PERIOD,
                    "AM",
                    "用户明确偏好上午号源",
                    Map.of("periodCode", "AM", "source", "USER_EXPLICIT"),
                    0.8,
                    "USER_EXPLICIT",
                    null
            );
        }
        if (containsAny(userMessage, "我习惯下午", "我偏好下午", "优先下午", "尽量下午",
                "下午的号", "挂下午", "约下午", "下午方便", "下午有空", "下午可以")) {
            upsertMemory(
                    userAccount,
                    MEMORY_TYPE_PREFERRED_PERIOD,
                    "PM",
                    "用户明确偏好下午号源",
                    Map.of("periodCode", "PM", "source", "USER_EXPLICIT"),
                    0.8,
                    "USER_EXPLICIT",
                    null
            );
        }
        // 科室提及: 挂<某某科> / 看<某某科>
        String dept = extractDepartmentMention(userMessage);
        if (dept != null) {
            upsertMemory(
                    userAccount,
                    MEMORY_TYPE_PREFERRED_DEPARTMENT,
                    dept,
                    "用户提及偏好科室" + dept,
                    Map.of("departmentName", dept, "source", "USER_MENTION"),
                    computeConfidence("USER_MENTION"),
                    "USER_MENTION",
                    null
            );
        }
        // 医生提及: X医生 / X主任 / X大夫
        String doctor = extractDoctorMention(userMessage);
        if (doctor != null) {
            upsertMemory(
                    userAccount,
                    MEMORY_TYPE_PREFERRED_DOCTOR,
                    doctor,
                    "用户提及偏好医生" + doctor,
                    Map.of("doctorRef", doctor, "source", "USER_MENTION"),
                    computeConfidence("USER_MENTION"),
                    "USER_MENTION",
                    null
            );
        }
    }

    private String extractDepartmentMention(String message) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("挂(.{1,6}科)").matcher(message);
        if (m.find()) {
            return m.group(1);
        }
        m = java.util.regex.Pattern.compile("看(.{1,6}科)").matcher(message);
        if (m.find()) {
            return m.group(1);
        }
        m = java.util.regex.Pattern.compile("(.{1,6}科室)").matcher(message);
        if (m.find()) {
            String dept = m.group(1);
            if (!containsAny(dept, "这个", "什么", "哪个", "你们")) {
                return dept;
            }
        }
        return null;
    }

    private String extractDoctorMention(String message) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("[\\u4e00-\\u9fff]{1,4}(医生|主任|大夫)").matcher(message);
        if (m.find()) {
            String match = m.group();
            if (!containsAny(match, "什么医生", "哪个医生", "什么主任", "哪个主任")) {
                return match;
            }
        }
        return null;
    }

    private void upsertMemory(UserAccountEntity userAccount,
                              String memoryType,
                              String memoryKey,
                              String summary,
                              Map<String, Object> value,
                              double confidence,
                              String source,
                              OffsetDateTime expiresAt) {
        if (!StringUtils.hasText(memoryKey)) {
            return;
        }
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime effectiveExpiry = expiresAt != null ? expiresAt : now.plusDays(DEFAULT_MEMORY_TTL_DAYS);
        UserMemoryProfileEntity entity = userMemoryProfileRepository
                .findByUserAccountAndMemoryTypeAndMemoryKey(userAccount, memoryType, memoryKey)
                .orElseGet(() -> UserMemoryProfileEntity.create(
                        userAccount,
                        memoryType,
                        memoryKey,
                        summary,
                        knowledgeJsonService.toJson(value),
                        confidence,
                        source,
                        now,
                        effectiveExpiry
                ));
        // 置信度合并：取已有与新值的较大者；同源多轮重复则递增
        double mergedConfidence = confidence;
        if (entity.getConfidence() != null) {
            double existingConf = entity.getConfidence().doubleValue();
            if (source.equals(entity.getSource()) && existingConf > 0 && existingConf < 0.9) {
                mergedConfidence = Math.min(0.9, existingConf + 0.1);
            } else {
                mergedConfidence = Math.max(existingConf, confidence);
            }
        }
        entity.refresh(summary, knowledgeJsonService.toJson(value), mergedConfidence, source, now, effectiveExpiry);
        userMemoryProfileRepository.save(entity);
    }

    private double computeConfidence(String source) {
        return switch (source) {
            case "BOOK_APPOINTMENT" -> 1.0;
            case "USER_EXPLICIT" -> 0.8;
            case "USER_MENTION" -> 0.3;
            case "TOOL_QUERY" -> 0.3;
            default -> 0.3;
        };
    }

    @Scheduled(cron = "0 0 3 * * ?")
    @Transactional
    public void cleanupExpiredMemories() {
        OffsetDateTime now = OffsetDateTime.now();
        java.util.List<UserMemoryProfileEntity> expired = userMemoryProfileRepository
                .findByExpiresAtBeforeAndStatus(now, "ACTIVE");
        for (UserMemoryProfileEntity entity : expired) {
            entity.expire();
        }
        userMemoryProfileRepository.saveAll(expired);
    }

    private List<String> relevantTypes(String intentType) {
        if (!StringUtils.hasText(intentType)) {
            return List.of();
        }
        if (intentType.contains("APPOINTMENT")
                || intentType.contains("SCHEDULE")
                || intentType.contains("DEPARTMENT")
                || intentType.contains("DOCTOR")) {
            return List.of(
                    MEMORY_TYPE_PREFERRED_DEPARTMENT,
                    MEMORY_TYPE_PREFERRED_DOCTOR,
                    MEMORY_TYPE_PREFERRED_PERIOD,
                    MEMORY_TYPE_LAST_SUCCESSFUL_APPOINTMENT
            );
        }
        if (intentType.contains("GENERAL") || intentType.contains("KNOWLEDGE")) {
            return List.of(
                    MEMORY_TYPE_PREFERRED_DEPARTMENT,
                    MEMORY_TYPE_PREFERRED_PERIOD
            );
        }
        return new ArrayList<>();
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

    private boolean containsAny(String value, String... patterns) {
        for (String pattern : patterns) {
            if (value.contains(pattern)) {
                return true;
            }
        }
        return false;
    }

    private String humanizePeriod(String periodCode) {
        if ("AM".equalsIgnoreCase(periodCode) || "MORNING".equalsIgnoreCase(periodCode)) {
            return "上午";
        }
        if ("PM".equalsIgnoreCase(periodCode) || "AFTERNOON".equalsIgnoreCase(periodCode)) {
            return "下午";
        }
        return StringUtils.hasText(periodCode) ? periodCode : "当前";
    }

    public record MemorySnippet(
            String memoryType,
            String memoryKey,
            String summary,
            Map<String, Object> value,
            double confidence,
            String source
    ) {
    }
}
