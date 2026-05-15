package com.medora.agent.application;

import com.medora.agent.tool.ToolNames;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

@Component
public class IntentRouter {

    private static final Pattern UUID_PATTERN = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"
    );
    private static final Pattern ISO_DATE_PATTERN = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");

    public IntentDecision route(String message) {
        String normalized = normalize(message);
        if (normalized.isBlank()) {
            return chatOnly("EMPTY_INPUT");
        }

        if (containsAny(normalized,
                "\u9884\u7ea6", "\u6302\u53f7", "\u53d6\u6d88", "\u53f7\u6e90", "\u533b\u751f",
                "\u79d1\u5ba4", "\u95e8\u8bca", "\u54b3\u55fd", "\u53d1\u70ed", "\u611f\u5192",
                "\u513f\u7ae5", "\u5b69\u5b50", "\u5c0f\u5b69")) {
            return routeMedicalService(normalized, message);
        }

        if (containsKnowledgeCue(normalized)) {
            return ragOnly("KNOWLEDGE_QA");
        }

        return chatOnly("GENERAL_CHAT");
    }

    private IntentDecision routeMedicalService(String normalized, String originalMessage) {
        if (containsAny(normalized, "\u53d6\u6d88\u9884\u7ea6", "\u53d6\u6d88\u6302\u53f7", "\u9000\u53f7", "\u53d6\u6d88")) {
            String appointmentId = firstUuid(originalMessage);
            if (appointmentId == null && containsKnowledgeCue(normalized)) {
                return ragOnly("KNOWLEDGE_QA");
            }
            if (appointmentId == null) {
                return toolOnly("CANCEL_APPOINTMENT_NEEDS_ID", ToolNames.GET_USER_APPOINTMENTS,
                        Map.of("status", "CONFIRMED", "pageSize", 5));
            }
            return toolOnly("CANCEL_APPOINTMENT", ToolNames.CANCEL_APPOINTMENT,
                    Map.of("appointmentId", appointmentId, "reason", "Agent \u5bf9\u8bdd\u53d6\u6d88"));
        }

        if (containsAny(normalized, "\u9884\u7ea6", "\u6302\u53f7") && firstUuid(originalMessage) != null) {
            return toolOnly("BOOK_APPOINTMENT", ToolNames.BOOK_APPOINTMENT,
                    Map.of("slotId", firstUuid(originalMessage)));
        }

        if (containsAny(normalized, "\u53f7\u6e90", "\u6392\u73ed", "\u53ef\u7ea6", "\u9884\u7ea6")) {
            Map<String, Object> arguments = new LinkedHashMap<>();
            parseDate(normalized).ifPresent(date -> arguments.put("scheduleDate", date.toString()));
            parsePeriod(normalized).ifPresent(period -> arguments.put("periodCode", period));
            String departmentKeyword = extractDepartmentKeyword(normalized);
            if (StringUtils.hasText(departmentKeyword)) {
                arguments.put("departmentKeyword", departmentKeyword);
            }
            arguments.put("pageSize", 5);
            return toolOnly("QUERY_SCHEDULE_SLOTS", ToolNames.QUERY_SCHEDULE_SLOTS, arguments);
        }

        if (containsAny(normalized, "\u533b\u751f", "\u4e13\u5bb6")) {
            return toolOnly("QUERY_DOCTORS", ToolNames.QUERY_DOCTORS,
                    Map.of("keyword", extractDoctorKeyword(normalized), "pageSize", 5));
        }

        if (containsAny(normalized,
                "\u79d1\u5ba4", "\u6302\u54ea\u4e2a\u79d1", "\u6302\u4ec0\u4e48\u79d1",
                "\u54b3\u55fd", "\u53d1\u70ed", "\u611f\u5192", "\u513f\u7ae5")) {
            return toolOnly("QUERY_DEPARTMENTS", ToolNames.QUERY_DEPARTMENTS,
                    Map.of("keyword", extractDepartmentKeyword(normalized), "pageSize", 5));
        }

        return ragOnly("MEDICAL_KNOWLEDGE_QA");
    }

    private IntentDecision chatOnly(String intentType) {
        return new IntentDecision(intentType, RouteMode.CHAT_ONLY, false, List.of());
    }

    private IntentDecision ragOnly(String intentType) {
        return new IntentDecision(intentType, RouteMode.RAG_ONLY, true, List.of());
    }

    private IntentDecision toolOnly(String intentType, String toolName, Map<String, Object> arguments) {
        return new IntentDecision(
                intentType,
                RouteMode.TOOL_ONLY,
                false,
                List.of(new IntentDecision.ToolInvocation(toolName, arguments))
        );
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim().replaceAll("\\s+", "") : "";
    }

    private boolean containsAny(String value, String... needles) {
        for (String needle : needles) {
            if (value.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private boolean containsKnowledgeCue(String value) {
        return containsAny(value, "\u89c4\u5219", "\u6d41\u7a0b", "\u8bf4\u660e", "\u9700\u8981", "\u600e\u4e48", "\u5982\u4f55",
                "\u4ec0\u4e48");
    }

    private String firstUuid(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        var matcher = UUID_PATTERN.matcher(value);
        return matcher.find() ? matcher.group() : null;
    }

    private Optional<LocalDate> parseDate(String value) {
        if (value.contains("\u540e\u5929")) {
            return Optional.of(LocalDate.now().plusDays(2));
        }
        if (value.contains("\u660e\u5929")) {
            return Optional.of(LocalDate.now().plusDays(1));
        }
        if (value.contains("\u4eca\u5929")) {
            return Optional.of(LocalDate.now());
        }
        var matcher = ISO_DATE_PATTERN.matcher(value);
        if (matcher.find()) {
            return Optional.of(LocalDate.parse(matcher.group()));
        }
        return Optional.empty();
    }

    private Optional<String> parsePeriod(String value) {
        if (value.contains("\u4e0a\u5348")) {
            return Optional.of("AM");
        }
        if (value.contains("\u4e0b\u5348")) {
            return Optional.of("PM");
        }
        return Optional.empty();
    }

    private String extractDoctorKeyword(String normalized) {
        String cleaned = normalized.replace("\u533b\u751f", "").replace("\u4e13\u5bb6", "");
        return cleaned.length() <= 8 ? cleaned : "";
    }

    private String extractDepartmentKeyword(String normalized) {
        List<String> candidates = new ArrayList<>();
        if (containsAny(normalized, "\u54b3\u55fd", "\u547c\u5438", "\u80ba")) {
            candidates.add("\u547c\u5438");
        }
        if (containsAny(normalized, "\u53d1\u70ed", "\u611f\u5192", "\u5185\u79d1")) {
            candidates.add("\u5185\u79d1");
        }
        if (containsAny(normalized, "\u513f\u7ae5", "\u5b69\u5b50", "\u5c0f\u5b69", "\u513f\u79d1")) {
            candidates.add("\u513f\u79d1");
        }
        return candidates.isEmpty() ? "" : candidates.getFirst();
    }
}
