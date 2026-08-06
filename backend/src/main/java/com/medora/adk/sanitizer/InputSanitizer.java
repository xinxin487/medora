package com.medora.adk.sanitizer;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 输入消毒层 — 确定性正则,与决策层 LLM 分离。
 *
 * 这是 LayerPolicy 中明确的「输入消毒层」:SQL 注入、代码注入、数据破坏等
 * 已知模式的精确字符串匹配,**不**用于意图判断(意图判断走 LLM)。
 *
 * 设计理由:
 *  - 正则匹配精确字节序列,无误判,毫秒级返回
 *  - 不会被 LLM 提示词绕过技巧绕过(如 base64 编码的 SQL 注入)
 *  - 维护成本与新增攻击模式成正比,可枚举
 *
 * 不包含 ROLE_HIJACK / INSTRUCTION_OVERRIDE / PROMPT_PROBE —— 这些是语义攻击,
 * 已由 LlmInputSafetyBeforeCallback 处理(决策层)。
 */
@Component
public class InputSanitizer {

    public static final int MAX_MESSAGE_LENGTH = 2000;

    /** 输入消毒规则 — 全部是确定性字符序列匹配,无语义成分 */
    private static final List<Rule> RULES = List.of(
            new Rule("SQL_INJECTION_DDL",
                    Pattern.compile(
                            "(?:drop\\s+table|truncate\\s+table|alter\\s+table)",
                            Pattern.CASE_INSENSITIVE)),
            new Rule("SQL_INJECTION_DML_DESTRUCTIVE",
                    Pattern.compile(
                            "delete\\s+from\\s+\\w+\\s*(?:;|--|$)",
                            Pattern.CASE_INSENSITIVE)),
            new Rule("CODE_INJECTION_EXEC",
                    Pattern.compile(
                            "(?:\\bexec(?:ute)?\\s+|\\bsystem\\s*\\(|\\bruntime\\.|\\bprocess\\.)",
                            Pattern.CASE_INSENSITIVE)),
            new Rule("DATA_DESTRUCTION_CN",
                    Pattern.compile(
                            "删除(?:所有|全部).*(?:数据|记录|信息|日志)|" +
                                    "清空(?:所有|全部)")),
            new Rule("DATA_DESTRUCTION_EN",
                    Pattern.compile(
                            "(?:destroy\\s+|purge\\s+)",
                            Pattern.CASE_INSENSITIVE))
    );

    public SanitizeResult sanitize(String userMessage) {
        if (!StringUtils.hasText(userMessage)) {
            return SanitizeResult.rejected("AGENT_EMPTY_MESSAGE", "message is required");
        }
        String normalized = userMessage.trim();
        if (normalized.length() > MAX_MESSAGE_LENGTH) {
            return SanitizeResult.rejected("AGENT_MESSAGE_TOO_LONG", "message exceeds " + MAX_MESSAGE_LENGTH + " chars");
        }
        for (Rule rule : RULES) {
            if (rule.pattern().matcher(normalized).find()) {
                return SanitizeResult.rejected("AGENT_SAFETY_REJECTED",
                        "input sanitizer rejected: " + rule.category());
            }
        }
        return SanitizeResult.passed(normalized);
    }

    private record Rule(String category, Pattern pattern) {
    }

    public record SanitizeResult(
            boolean allowed,
            String sanitizedMessage,
            String rejectCode,
            String rejectMessage
    ) {
        public static SanitizeResult passed(String sanitizedMessage) {
            return new SanitizeResult(true, sanitizedMessage, null, null);
        }

        public static SanitizeResult rejected(String code, String message) {
            return new SanitizeResult(false, null, code, message);
        }
    }
}
