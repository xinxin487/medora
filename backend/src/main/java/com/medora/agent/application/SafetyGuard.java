package com.medora.agent.application;

import com.medora.common.exception.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.regex.Pattern;

@Component
public class SafetyGuard {

    private static final int MAX_MESSAGE_LENGTH = 2000;

    private static final List<Rule> BLOCKED_RULES = List.of(
            // 角色劫持变体
            new Rule("ROLE_HIJACK", Pattern.compile(
                    "(?:你(?:现在|将|要)(?:是|扮演|作为|成为)|" +
                            "act\\s+as\\s+|" +
                            "pretend\\s+(?:to\\s+be\\s+|you\\s+are\\s+)|" +
                            "roleplay|" +
                            "从现在起你)",
                    Pattern.CASE_INSENSITIVE)),
            // 指令覆盖变体
            new Rule("INSTRUCTION_OVERRIDE", Pattern.compile(
                    "(?:忽略(?:以上|所有|系统)?(?:指令|规则|提示|要求)|" +
                            "ignore\\s+(?:all\\s+)?(?:above|previous|system|instructions|rules)|" +
                            "bypass\\s+(?:rules?|restrictions?|guardrails?)|" +
                            "override|" +
                            "disregard(?:\\s+previous)?)",
                    Pattern.CASE_INSENSITIVE)),
            // 系统提示探查变体
            new Rule("PROMPT_PROBE", Pattern.compile(
                    "(?:你的(?:系统)?(?:提示|指令|规则|要求|prompt|system\\s*prompt)(?:是|是什么|有哪些)|" +
                            "what\\s+(?:are\\s+)?(?:your\\s+)?(?:instructions?|rules?|system\\s*(?:prompt)?|prompt)|" +
                            "repeat\\s+(?:above|the\\s+(?:above|system|initial)\\s+(?:prompt|text|message))|" +
                            "输出你的(?:prompt|提示词|系统指令))",
                    Pattern.CASE_INSENSITIVE)),
            // SQL/代码注入变体
            new Rule("CODE_INJECTION", Pattern.compile(
                    "(?:drop\\s+table|" +
                            "truncate\\s+table|" +
                            "delete\\s+from|" +
                            "exec(?:ute)?\\s+|" +
                            "system\\(|" +
                            "runtime\\.|" +
                            "process\\.|" +
                            "直接操作数据库|" +
                            "绕过工具)",
                    Pattern.CASE_INSENSITIVE)),
            // 数据破坏变体
            new Rule("DATA_DESTRUCTION", Pattern.compile(
                    "删除(?:所有|全部).*(?:数据|记录|信息|日志|对话)|" +
                            "清空(?:所有|全部)|" +
                            "destroy|" +
                            "purge",
                    Pattern.CASE_INSENSITIVE))
    );

    private static final List<String> BLOCKED_PATTERNS = List.of(
            "忽略以上规则",
            "绕过工具",
            "直接操作数据库",
            "删除所有",
            "drop table",
            "truncate table"
    );

    private final List<Rule> activeRules;

    public SafetyGuard() {
        this.activeRules = BLOCKED_RULES;
    }

    public CheckResult preCheck(String userMessage) {
        if (!StringUtils.hasText(userMessage)) {
            return new CheckResult(false, "AGENT_EMPTY_MESSAGE", "message is required");
        }
        String normalized = userMessage.trim();
        if (normalized.length() > MAX_MESSAGE_LENGTH) {
            return new CheckResult(false, "AGENT_MESSAGE_TOO_LONG", "message is too long");
        }

        // 正则规则匹配
        String lower = normalized.toLowerCase();
        for (Rule rule : activeRules) {
            if (rule.pattern().matcher(lower).find()) {
                return new CheckResult(false, "AGENT_SAFETY_REJECTED",
                        "message violates agent safety policy: " + rule.category());
            }
        }

        // 遗留的精确匹配
        for (String pattern : BLOCKED_PATTERNS) {
            if (lower.contains(pattern.toLowerCase())) {
                return new CheckResult(false, "AGENT_SAFETY_REJECTED",
                        "message violates agent safety policy: " + pattern);
            }
        }

        return new CheckResult(true, null, null);
    }

    public void validate(String userMessage) {
        CheckResult result = preCheck(userMessage);
        if (!result.allowed()) {
            throw new BusinessException(result.code(), HttpStatus.UNPROCESSABLE_ENTITY, result.message());
        }
    }

    private record Rule(String category, Pattern pattern) {
    }

    public record CheckResult(
            boolean allowed,
            String code,
            String message
    ) {
    }
}
