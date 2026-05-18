package com.medora.memory.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medora.agent.application.AgentRunResult;
import com.medora.agent.tool.ToolExecutionResult;
import com.medora.appointment.api.response.AppointmentResponse;
import com.medora.auth.domain.UserAccountEntity;
import com.medora.knowledge.service.KnowledgeJsonService;
import com.medora.memory.domain.UserMemoryProfileEntity;
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
        captureExplicitPeriodPreference(userAccount, userMessage);
        for (ToolExecutionResult result : runResult.toolResults()) {
            if (!result.success() || !(result.data() instanceof AppointmentResponse appointment)) {
                continue;
            }
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
                    0.88,
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
                    0.84,
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
                    0.82,
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
                    0.92,
                    "BOOK_APPOINTMENT",
                    null
            );
        }
    }

    private void captureExplicitPeriodPreference(UserAccountEntity userAccount, String userMessage) {
        if (!StringUtils.hasText(userMessage)) {
            return;
        }
        if (containsAny(userMessage, "我习惯上午", "我偏好上午", "优先上午", "尽量上午")) {
            upsertMemory(
                    userAccount,
                    MEMORY_TYPE_PREFERRED_PERIOD,
                    "AM",
                    "用户明确偏好上午号源",
                    Map.of("periodCode", "AM", "source", "USER_EXPLICIT"),
                    0.75,
                    "USER_EXPLICIT",
                    null
            );
        }
        if (containsAny(userMessage, "我习惯下午", "我偏好下午", "优先下午", "尽量下午")) {
            upsertMemory(
                    userAccount,
                    MEMORY_TYPE_PREFERRED_PERIOD,
                    "PM",
                    "用户明确偏好下午号源",
                    Map.of("periodCode", "PM", "source", "USER_EXPLICIT"),
                    0.75,
                    "USER_EXPLICIT",
                    null
            );
        }
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
                        expiresAt
                ));
        entity.refresh(summary, knowledgeJsonService.toJson(value), confidence, source, now, expiresAt);
        userMemoryProfileRepository.save(entity);
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
