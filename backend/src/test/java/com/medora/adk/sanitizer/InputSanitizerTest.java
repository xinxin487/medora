package com.medora.adk.sanitizer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * InputSanitizer 单测 — 输入消毒层(确定性正则)。
 *
 * 设计验证:
 *  - 正常消息通过
 *  - 各类注入模式被精确拒绝
 *  - 长度限制生效
 *  - 空消息被拒绝
 *  - 正常医学术语不误判(如「诊断」「用药建议」等不应在消毒层被拦)
 */
class InputSanitizerTest {

    private final InputSanitizer sanitizer = new InputSanitizer();

    @Test
    void passesNormalMedicalQuery() {
        InputSanitizer.SanitizeResult result = sanitizer.sanitize("我想了解一下心内科能不能挂号");
        assertThat(result.allowed()).isTrue();
        assertThat(result.sanitizedMessage()).isEqualTo("我想了解一下心内科能不能挂号");
    }

    @Test
    void rejectsEmptyMessage() {
        InputSanitizer.SanitizeResult result = sanitizer.sanitize("");
        assertThat(result.allowed()).isFalse();
        assertThat(result.rejectCode()).isEqualTo("AGENT_EMPTY_MESSAGE");
    }

    @Test
    void rejectsNullMessage() {
        InputSanitizer.SanitizeResult result = sanitizer.sanitize(null);
        assertThat(result.allowed()).isFalse();
        assertThat(result.rejectCode()).isEqualTo("AGENT_EMPTY_MESSAGE");
    }

    @Test
    void rejectsTooLongMessage() {
        String longMsg = "a".repeat(InputSanitizer.MAX_MESSAGE_LENGTH + 1);
        InputSanitizer.SanitizeResult result = sanitizer.sanitize(longMsg);
        assertThat(result.allowed()).isFalse();
        assertThat(result.rejectCode()).isEqualTo("AGENT_MESSAGE_TOO_LONG");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "DROP TABLE conversation",
            "truncate table users",
            "delete from user_account;",
            "EXEC master.dbo.xp_cmdshell",
            "system(/bin/rm -rf)",
            "Runtime.getRuntime().exec",
            "删除所有数据",
            "清空所有记录",
            "DESTROY the database",
            "PURGE the table"
    })
    void rejectsKnownInjectionPatterns(String input) {
        InputSanitizer.SanitizeResult result = sanitizer.sanitize(input);
        assertThat(result.allowed())
                .as("should reject: %s", input)
                .isFalse();
        assertThat(result.rejectCode()).isEqualTo("AGENT_SAFETY_REJECTED");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "我想约个心内科",
            "怎么取消预约",
            "帮我看下检查结果",
            "诊断证明怎么开",
            "用药物治疗还是手术治疗"  // 注意:消毒层不拦「诊断」「用药」这种正常医疗用语,那是 LLM 输出安全层的职责
    })
    void doesNotFalsePositiveOnNormalMedical(String input) {
        InputSanitizer.SanitizeResult result = sanitizer.sanitize(input);
        assertThat(result.allowed())
                .as("should pass: %s", input)
                .isTrue();
    }

    @Test
    void trimsWhitespaceBeforeLengthCheck() {
        String padded = "  ".repeat(50) + "心内科" + "  ".repeat(50);
        InputSanitizer.SanitizeResult result = sanitizer.sanitize(padded);
        assertThat(result.allowed()).isTrue();
        assertThat(result.sanitizedMessage()).isEqualTo("心内科");
    }
}
