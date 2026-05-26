package com.medora.agent.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

@Component
public class OutputSafetyGuard {

    private static final Logger log = LoggerFactory.getLogger(OutputSafetyGuard.class);

    private static final List<Pattern> DIAGNOSTIC_PATTERNS = List.of(
            Pattern.compile("你(?:患有|得了|被诊断(?:为|有))", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?:确诊|诊断为)\\s*[：:].+", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?:病情|病症)(?:是|为)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("建议(?:服用|使用|吃|注射).+药物", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?:用药|服药)(?:方案|建议|指导)", Pattern.CASE_INSENSITIVE)
    );

    private static final Pattern VALID_APPOINTMENT_NO = Pattern.compile("APT\\d{8}[A-F0-9]{8}");

    private static final List<Pattern> SYSTEM_INFO_LEAK_PATTERNS = List.of(
            Pattern.compile("trace_\\w{20,}", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\btraceId\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bslotId\\b"),  // slotId is ok in context, but leaking raw IDs is not
            Pattern.compile("AGENT_TOOL_\\w+"),
            Pattern.compile("(?:toolName|toolResults|retrievalResults|intentType|routeMode)"),
            Pattern.compile("\\bexec\\.\\w+\\b"),
            Pattern.compile("\\bjava\\.\\w+\\b")
    );

    public CheckResult check(String reply) {
        if (reply == null || reply.isBlank()) {
            return new CheckResult(true, null, null);
        }

        // 检测诊断性语言
        for (Pattern pattern : DIAGNOSTIC_PATTERNS) {
            if (pattern.matcher(reply).find()) {
                log.warn("OutputSafetyGuard detected diagnostic language in reply");
                return new CheckResult(false, "OUTPUT_DIAGNOSTIC_LANGUAGE",
                        "回复包含诊断性语言，已追加医疗免责声明",
                        SafetyAction.APPEND_DISCLAIMER);
            }
        }

        // 检测系统内部信息泄露
        for (Pattern pattern : SYSTEM_INFO_LEAK_PATTERNS) {
            if (pattern.matcher(reply).find()) {
                log.warn("OutputSafetyGuard detected potential system info leak in reply");
                return new CheckResult(false, "OUTPUT_SYSTEM_INFO_LEAK",
                        "回复可能泄露系统内部信息",
                        SafetyAction.APPEND_DISCLAIMER);
            }
        }

        return new CheckResult(true, null, null);
    }

    public record CheckResult(
            boolean allowed,
            String reasonCode,
            String reasonMessage,
            SafetyAction action
    ) {
        public CheckResult(boolean allowed, String reasonCode, String reasonMessage) {
            this(allowed, reasonCode, reasonMessage, SafetyAction.NONE);
        }
    }

    public enum SafetyAction {
        NONE,
        APPEND_DISCLAIMER,
        REPLACE_REPLY
    }
}
