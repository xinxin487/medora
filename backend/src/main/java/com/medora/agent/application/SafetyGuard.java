package com.medora.agent.application;

import com.medora.common.exception.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

@Component
public class SafetyGuard {

    private static final int MAX_MESSAGE_LENGTH = 2000;
    private static final List<String> BLOCKED_PATTERNS = List.of(
            "忽略以上规则",
            "绕过工具",
            "直接操作数据库",
            "删除所有",
            "drop table",
            "truncate table"
    );

    public void preCheck(String userMessage) {
        if (!StringUtils.hasText(userMessage)) {
            throw new BusinessException("AGENT_EMPTY_MESSAGE", HttpStatus.BAD_REQUEST, "message is required");
        }
        String normalized = userMessage.trim();
        if (normalized.length() > MAX_MESSAGE_LENGTH) {
            throw new BusinessException("AGENT_MESSAGE_TOO_LONG", HttpStatus.BAD_REQUEST, "message is too long");
        }
        String lower = normalized.toLowerCase();
        for (String pattern : BLOCKED_PATTERNS) {
            if (lower.contains(pattern.toLowerCase())) {
                throw new BusinessException("AGENT_SAFETY_REJECTED", HttpStatus.UNPROCESSABLE_ENTITY,
                        "message violates agent safety policy");
            }
        }
    }
}
