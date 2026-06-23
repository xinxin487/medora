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

class LlmHallucinationAfterCallbackTest {

    private LlmSafetyClient llmSafetyClient;
    private LlmHallucinationAfterCallback callback;

    @BeforeEach
    void setUp() {
        llmSafetyClient = mock(LlmSafetyClient.class);
        callback = new LlmHallucinationAfterCallback(llmSafetyClient, new ObjectMapper());
    }

    @Test
    void highScorePasses() {
        when(llmSafetyClient.call(eq("hallucinationCheck"), anyString(), anyString()))
                .thenReturn("{\"score\": 0.9, \"unsupported_claims\": [], \"reason\": \"引用充分\"}");

        LlmHallucinationAfterCallback.HallucinationScore score =
                callback.score("高血压常见症状包括头晕", "高血压常见症状包括头晕、头痛");

        assertThat(score.score()).isEqualTo(0.9);
        assertThat(score.highRisk()).isFalse();
    }

    @Test
    void lowScoreFlagsHighRisk() {
        when(llmSafetyClient.call(eq("hallucinationCheck"), anyString(), anyString()))
                .thenReturn("{\"score\": 0.3, \"unsupported_claims\": [\"编造的药名\"], \"reason\": \"无引用支撑\"}");

        LlmHallucinationAfterCallback.HallucinationScore score =
                callback.score("服用 XYZ 药物可以根治", "高血压需要长期管理");

        assertThat(score.score()).isEqualTo(0.3);
        assertThat(score.highRisk()).isTrue();
        assertThat(score.unsupportedClaims()).contains("编造的药名");
    }

    @Test
    void scoreThresholdIs0_6() {
        assertThat(LlmHallucinationAfterCallback.SCORE_THRESHOLD).isEqualTo(0.6);
    }

    @Test
    void borderlineScore60NotHighRisk() {
        when(llmSafetyClient.call(eq("hallucinationCheck"), anyString(), anyString()))
                .thenReturn("{\"score\": 0.6}");

        LlmHallucinationAfterCallback.HallucinationScore score =
                callback.score("回复", "引用");

        assertThat(score.highRisk()).isFalse();
    }

    @Test
    void llmFailurePropagatesAsLlmUnavailable() {
        when(llmSafetyClient.call(eq("hallucinationCheck"), anyString(), anyString()))
                .thenThrow(new com.medora.common.exception.LlmUnavailableException(
                        "hallucinationCheck", new java.io.IOException("timeout")));

        assertThatThrownBy(() -> callback.score("回复", "引用"))
                .isInstanceOf(com.medora.common.exception.LlmUnavailableException.class);
    }

    @Test
    void llmReturnsMalformedJsonDefaultsToZero() {
        when(llmSafetyClient.call(eq("hallucinationCheck"), anyString(), anyString()))
                .thenReturn("not json");

        LlmHallucinationAfterCallback.HallucinationScore score =
                callback.score("回复", "引用");

        // 保守起见,解析失败视为高风险(score=0)
        assertThat(score.score()).isEqualTo(0.0);
        assertThat(score.highRisk()).isTrue();
    }
}
