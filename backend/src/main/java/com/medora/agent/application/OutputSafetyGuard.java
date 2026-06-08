package com.medora.agent.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 输出安全防护 — 正则快速检测 + LLM 语义检测双层。
 * 检测回复中是否包含医疗诊断、用药建议、系统信息泄露等内容。
 */
@Component
public class OutputSafetyGuard {

    private static final Logger log = LoggerFactory.getLogger(OutputSafetyGuard.class);

    // 第一层：正则快速检测已知的诊断性语言模式
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
            Pattern.compile("\\bslotId\\b"),
            Pattern.compile("AGENT_TOOL_\\w+"),
            Pattern.compile("(?:toolName|toolResults|retrievalResults|intentType|routeMode)"),
            Pattern.compile("\\bexec\\.\\w+\\b"),
            Pattern.compile("\\bjava\\.\\w+\\b")
    );

    private static final String LLM_OUTPUT_SAFETY_PROMPT = """
            你是 Medora 医疗助手的输出安全审查模块。请检查助手回复是否包含以下问题：
            1. 医疗诊断：是否对患者做出了疾病诊断（如"你患有XX"、"确诊为XX"）
            2. 用药建议：是否推荐了具体药物（如"吃XX药"、"服用XX"），注意区分"建议就医"和"推荐药物"
            3. 系统信息泄露：是否暴露了内部系统信息（工具名称、内部 ID、代码、异常堆栈）
            4. 危险操作建议：是否建议了危险行为

            只输出 JSON：
            {"safe": true/false, "hasDiagnostic": true/false, "hasMedicationAdvice": true/false, "hasSystemLeak": true/false, "reason": "原因"}
            不要输出任何其他内容。
            """;

    private final AgentModelGateway agentModelGateway;

    public OutputSafetyGuard(AgentModelGateway agentModelGateway) {
        this.agentModelGateway = agentModelGateway;
    }

    /**
     * 正则快速检测（毫秒级，同步）。
     */
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

    /**
     * 双层检查：正则快速检测 + LLM 语义检测。
     * 正则通过后才调用 LLM，避免不必要的 API 调用。
     */
    public CheckResult fullCheck(String reply) {
        CheckResult regexResult = check(reply);
        if (!regexResult.allowed()) {
            return regexResult;
        }

        // LLM 语义检测
        return llmOutputCheck(reply);
    }

    /**
     * LLM 语义级输出安全审查 — 捕获正则无法覆盖的诊断/用药建议变体。
     */
    private CheckResult llmOutputCheck(String reply) {
        try {
            String llmPrompt = "请审查以下助手回复是否安全：\n" + reply;
            Optional<String> response = agentModelGateway.lightweightLlmCall(
                    LLM_OUTPUT_SAFETY_PROMPT, llmPrompt);
            if (response.isPresent()) {
                String content = response.get().trim();
                boolean hasDiagnostic = content.contains("\"hasDiagnostic\": true") || content.contains("\"hasDiagnostic\":true");
                boolean hasMedication = content.contains("\"hasMedicationAdvice\": true") || content.contains("\"hasMedicationAdvice\":true");
                boolean hasLeak = content.contains("\"hasSystemLeak\": true") || content.contains("\"hasSystemLeak\":true");

                if (hasDiagnostic || hasMedication) {
                    log.warn("LLM OutputSafetyGuard detected medical advice. llmResponse={}", content);
                    return new CheckResult(false, "OUTPUT_LLM_MEDICAL_ADVICE",
                            "LLM 检测到回复包含诊断或用药建议",
                            SafetyAction.APPEND_DISCLAIMER);
                }
                if (hasLeak) {
                    log.warn("LLM OutputSafetyGuard detected system leak. llmResponse={}", content);
                    return new CheckResult(false, "OUTPUT_LLM_SYSTEM_LEAK",
                            "LLM 检测到回复可能泄露系统信息",
                            SafetyAction.REPLACE_REPLY);
                }
            }
        } catch (Exception ex) {
            log.warn("LLM output safety check failed, using regex result only. error={}", ex.getMessage());
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
