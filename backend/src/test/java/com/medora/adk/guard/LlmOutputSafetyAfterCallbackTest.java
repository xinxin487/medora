package com.medora.adk.guard;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LlmOutputSafetyAfterCallbackTest {

    private LlmSafetyClient llmSafetyClient;
    private LlmOutputSafetyAfterCallback callback;

    @BeforeEach
    void setUp() {
        llmSafetyClient = mock(LlmSafetyClient.class);
        callback = new LlmOutputSafetyAfterCallback(llmSafetyClient, new ObjectMapper());
    }

    @Test
    void emptyReplyTreatedAsSafe() {
        LlmOutputSafetyAfterCallback.SafetyDecision decision = callback.check(null);
        assertThat(decision.safe()).isTrue();
    }

    @Test
    void blankReplyTreatedAsSafe() {
        LlmOutputSafetyAfterCallback.SafetyDecision decision = callback.check("   ");
        assertThat(decision.safe()).isTrue();
    }

    @Test
    void llmJudgesSafe() {
        when(llmSafetyClient.call(eq("outputSafetyCheck"), anyString(), anyString()))
                .thenReturn("{\"safe\": true, \"hasDiagnostic\": false, \"hasMedicationAdvice\": false, \"hasSystemLeak\": false}");

        LlmOutputSafetyAfterCallback.SafetyDecision decision =
                callback.check("您可以在心内科挂号,推荐上午时段。");

        assertThat(decision.safe()).isTrue();
    }

    @Test
    void llmFlagsDiagnosticTriggersDisclaimer() {
        when(llmSafetyClient.call(eq("outputSafetyCheck"), anyString(), anyString()))
                .thenReturn("{\"safe\": false, \"hasDiagnostic\": true, \"hasMedicationAdvice\": false, \"hasSystemLeak\": false, \"reason\": \"包含诊断\"}");

        LlmOutputSafetyAfterCallback.SafetyDecision decision =
                callback.check("您可能患有高血压");

        assertThat(decision.safe()).isFalse();
        assertThat(decision.action()).isEqualTo(LlmOutputSafetyAfterCallback.OutputAction.APPEND_DISCLAIMER);
    }

    @Test
    void llmFlagsSystemLeakTriggersReplace() {
        when(llmSafetyClient.call(eq("outputSafetyCheck"), anyString(), anyString()))
                .thenReturn("{\"safe\": false, \"hasDiagnostic\": false, \"hasMedicationAdvice\": false, \"hasSystemLeak\": true, \"reason\": \"泄露\"}");

        LlmOutputSafetyAfterCallback.SafetyDecision decision =
                callback.check("工具执行错误:toolName=bookAppointment");

        assertThat(decision.safe()).isFalse();
        assertThat(decision.action()).isEqualTo(LlmOutputSafetyAfterCallback.OutputAction.REPLACE_REPLY);
    }

    @Test
    void llmCallFailurePropagatesAsLlmUnavailable() {
        when(llmSafetyClient.call(eq("outputSafetyCheck"), anyString(), anyString()))
                .thenThrow(new com.medora.common.exception.LlmUnavailableException(
                        "outputSafetyCheck", new java.io.IOException("connection reset")));

        assertThatThrownBy(() -> callback.check("正常回复"))
                .isInstanceOf(com.medora.common.exception.LlmUnavailableException.class);
    }

    @Test
    void llmReturnsMalformedJsonTreatedAsReplace() {
        when(llmSafetyClient.call(eq("outputSafetyCheck"), anyString(), anyString()))
                .thenReturn("not json");

        LlmOutputSafetyAfterCallback.SafetyDecision decision = callback.check("回复");
        assertThat(decision.safe()).isFalse();
        assertThat(decision.action()).isEqualTo(LlmOutputSafetyAfterCallback.OutputAction.REPLACE_REPLY);
    }
}
