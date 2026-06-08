package com.medora.agent.application;

import com.medora.agent.tool.ToolNames;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 本地意图路由器 — LLM Planner 的降级方案。
 * 当 LLM 不可用时，通过关键词匹配提供基本的意图识别。
 * LLM 可用时由 AgentModelGateway.planIntent() 优先处理。
 */
@Component
public class IntentRouter {

    private static final Pattern UUID_PATTERN = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"
    );
    private static final Pattern ISO_DATE_PATTERN = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");
    private static final Pattern CHINESE_DATE_PATTERN = Pattern.compile(
            "(\\d{1,2})月(\\d{1,2})[号日]"
    );
    private static final Pattern DAYS_LATER_PATTERN = Pattern.compile("(\\d+)天[之以]?后");
    private static final Pattern WEEKDAY_PATTERN = Pattern.compile(
            "(?:下|这)?周([一二三四五六日天])"
    );

    public IntentDecision route(String message) {
        String normalized = normalize(message);
        if (normalized.isBlank()) {
            return chatOnly("EMPTY_INPUT");
        }

        if (containsAny(normalized,
                "预约", "挂号", "取消", "号源", "医生",
                "科室", "门诊", "咳嗽", "发热", "感冒",
                "儿童", "孩子", "小孩", "头晕", "头痛",
                "肚子", "胃", "皮肤", "痒", "眼睛", "视力",
                "骨", "腰", "关节", "牙", "失眠", "焦虑",
                "妇科", "耳", "鼻", "喉", "不舒服", "难受",
                "疼", "痛", "痒", "出血", "过敏", "恶心",
                "呕吐", "拉肚子", "便秘", "心悸", "胸闷")) {
            return routeMedicalService(normalized, message);
        }

        if (containsKnowledgeCue(normalized)) {
            return ragOnly("KNOWLEDGE_QA");
        }

        return chatOnly("GENERAL_CHAT");
    }

    private IntentDecision routeMedicalService(String normalized, String originalMessage) {
        if (containsAny(normalized, "取消预约", "取消挂号", "退号", "取消")) {
            String appointmentId = firstUuid(originalMessage);
            if (appointmentId == null && containsKnowledgeCue(normalized)) {
                return ragOnly("KNOWLEDGE_QA");
            }
            if (appointmentId == null) {
                return toolOnly("CANCEL_APPOINTMENT_NEEDS_ID", ToolNames.GET_USER_APPOINTMENTS,
                        Map.of("status", "CONFIRMED", "pageSize", 5));
            }
            return toolOnly("CANCEL_APPOINTMENT", ToolNames.CANCEL_APPOINTMENT,
                    Map.of("appointmentId", appointmentId, "reason", "Agent 对话取消"));
        }

        if (containsAny(normalized, "预约", "挂号") && firstUuid(originalMessage) != null) {
            return toolOnly("BOOK_APPOINTMENT", ToolNames.BOOK_APPOINTMENT,
                    Map.of("slotId", firstUuid(originalMessage)));
        }

        if (containsAny(normalized, "号源", "排班", "可约", "预约")) {
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

        if (containsAny(normalized, "医生", "专家")) {
            return toolOnly("QUERY_DOCTORS", ToolNames.QUERY_DOCTORS,
                    Map.of("keyword", extractDoctorKeyword(normalized), "pageSize", 5));
        }

        if (containsAny(normalized,
                "科室", "挂哪个科", "挂什么科",
                "咳嗽", "发热", "感冒", "儿童",
                "头晕", "头痛", "肚子", "胃",
                "皮肤", "眼睛", "骨", "腰", "牙",
                "失眠", "焦虑", "妇科", "耳", "鼻", "喉",
                "不舒服", "难受", "疼", "痛", "过敏", "恶心")) {
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
                List.of(IntentDecision.ToolInvocation.userProvided(toolName, arguments))
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
        return containsAny(value, "规则", "流程", "说明", "需要", "怎么", "如何",
                "什么", "费用", "多少钱", "医保", "报销", "能不能", "可不可以",
                "注意", "要求", "准备", "带什么", "挂号费", "门诊时间");
    }

    private String firstUuid(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        var matcher = UUID_PATTERN.matcher(value);
        return matcher.find() ? matcher.group() : null;
    }

    /**
     * 增强版时间解析：支持今天/明天/后天/大后天、下周X、本周X、X天后、X月X日、X号
     */
    private Optional<LocalDate> parseDate(String value) {
        // 固定偏移
        if (value.contains("大后天")) return Optional.of(LocalDate.now().plusDays(3));
        if (value.contains("后天")) return Optional.of(LocalDate.now().plusDays(2));
        if (value.contains("明天")) return Optional.of(LocalDate.now().plusDays(1));
        if (value.contains("今天")) return Optional.of(LocalDate.now());

        // "X天后"
        var daysLaterMatcher = DAYS_LATER_PATTERN.matcher(value);
        if (daysLaterMatcher.find()) {
            try {
                int days = Integer.parseInt(daysLaterMatcher.group(1));
                if (days > 0 && days <= 30) {
                    return Optional.of(LocalDate.now().plusDays(days));
                }
            } catch (NumberFormatException ignored) {}
        }

        // "下周X" / "这周X" / "周X"
        var weekdayMatcher = WEEKDAY_PATTERN.matcher(value);
        if (weekdayMatcher.find()) {
            DayOfWeek target = parseChineseWeekday(weekdayMatcher.group(1));
            if (target != null) {
                boolean isNextWeek = value.contains("下周");
                boolean isThisWeek = value.contains("这周") || value.contains("本周");
                if (isNextWeek) {
                    return Optional.of(LocalDate.now().with(TemporalAdjusters.next(target)));
                }
                if (isThisWeek) {
                    LocalDate targetDate = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                            .with(TemporalAdjusters.nextOrSame(target));
                    return Optional.of(targetDate);
                }
                // 无前缀的"周X"视为最近的目标日（含今天）
                LocalDate next = LocalDate.now().with(TemporalAdjusters.nextOrSame(target));
                return Optional.of(next);
            }
        }

        // "X月X号" / "X月X日"
        var chineseDateMatcher = CHINESE_DATE_PATTERN.matcher(value);
        if (chineseDateMatcher.find()) {
            try {
                int month = Integer.parseInt(chineseDateMatcher.group(1));
                int day = Integer.parseInt(chineseDateMatcher.group(2));
                int year = LocalDate.now().getYear();
                LocalDate parsed = LocalDate.of(year, month, day);
                // 如果日期已过，视为明年
                if (parsed.isBefore(LocalDate.now())) {
                    parsed = LocalDate.of(year + 1, month, day);
                }
                return Optional.of(parsed);
            } catch (Exception ignored) {}
        }

        // ISO 格式 yyyy-MM-dd
        var matcher = ISO_DATE_PATTERN.matcher(value);
        if (matcher.find()) {
            try {
                return Optional.of(LocalDate.parse(matcher.group()));
            } catch (Exception ignored) {}
        }

        return Optional.empty();
    }

    private DayOfWeek parseChineseWeekday(String chinese) {
        return switch (chinese) {
            case "一" -> DayOfWeek.MONDAY;
            case "二" -> DayOfWeek.TUESDAY;
            case "三" -> DayOfWeek.WEDNESDAY;
            case "四" -> DayOfWeek.THURSDAY;
            case "五" -> DayOfWeek.FRIDAY;
            case "六" -> DayOfWeek.SATURDAY;
            case "日", "天" -> DayOfWeek.SUNDAY;
            default -> null;
        };
    }

    /**
     * 增强版时段解析：支持更多时间段表达
     */
    private Optional<String> parsePeriod(String value) {
        if (containsAny(value, "上午", "早上", "早晨", "早间", "午饭前")) {
            return Optional.of("AM");
        }
        if (containsAny(value, "下午", "晚上", "傍晚", "午后", "晚间")) {
            return Optional.of("PM");
        }
        if (value.contains("中午")) {
            // 中午归为上午
            return Optional.of("AM");
        }
        return Optional.empty();
    }

    private String extractDoctorKeyword(String normalized) {
        String cleaned = normalized.replace("医生", "").replace("专家", "");
        return cleaned.length() <= 8 ? cleaned : "";
    }

    /**
     * 增强版症状→科室映射：覆盖更多常见症状
     */
    private String extractDepartmentKeyword(String normalized) {
        List<String> candidates = new ArrayList<>();

        // 呼吸系统
        if (containsAny(normalized, "咳嗽", "呼吸", "肺", "气喘", "哮喘", "胸闷", "憋气")) {
            candidates.add("呼吸");
        }
        // 内科/发热
        if (containsAny(normalized, "发热", "感冒", "内科", "发烧", "低烧", "高烧")) {
            candidates.add("内科");
        }
        // 儿科
        if (containsAny(normalized, "儿童", "孩子", "小孩", "儿科", "宝宝", "婴儿")) {
            candidates.add("儿科");
        }
        // 神经内科
        if (containsAny(normalized, "头晕", "头痛", "偏头痛", "头疼", "眩晕", "失眠", "记忆力")) {
            candidates.add("神经内科");
        }
        // 消化内科
        if (containsAny(normalized, "胃", "肚子", "腹泻", "拉肚子", "消化", "便秘", "恶心", "呕吐", "反酸")) {
            candidates.add("消化内科");
        }
        // 皮肤科
        if (containsAny(normalized, "皮肤", "痒", "红疹", "皮疹", "过敏", "湿疹", "荨麻")) {
            candidates.add("皮肤科");
        }
        // 眼科
        if (containsAny(normalized, "眼睛", "视力", "近视", "眼红", "眼干", "飞蚊")) {
            candidates.add("眼科");
        }
        // 骨科
        if (containsAny(normalized, "骨", "腰", "关节", "骨折", "颈椎", "腰椎", "膝盖", "肩膀", "扭伤")) {
            candidates.add("骨科");
        }
        // 口腔科
        if (containsAny(normalized, "牙", "牙龈", "口腔", "蛀牙", "智齿", "口臭")) {
            candidates.add("口腔科");
        }
        // 心理科/精神科
        if (containsAny(normalized, "失眠", "焦虑", "抑郁", "心理", "情绪", "压力", "紧张")) {
            candidates.add("心理科");
        }
        // 妇科
        if (containsAny(normalized, "妇科", "月经", "白带", "怀孕", "产检")) {
            candidates.add("妇科");
        }
        // 耳鼻喉科
        if (containsAny(normalized, "耳", "鼻", "喉", "耳鸣", "鼻塞", "咽喉", "扁桃体", "打鼾")) {
            candidates.add("耳鼻喉");
        }
        // 心血管内科
        if (containsAny(normalized, "心悸", "心慌", "心跳", "胸闷", "高血压", "心脏")) {
            candidates.add("心血管");
        }
        // 泌尿外科
        if (containsAny(normalized, "尿", "肾", "前列腺", "膀胱")) {
            candidates.add("泌尿");
        }

        return candidates.isEmpty() ? "" : candidates.getFirst();
    }
}
