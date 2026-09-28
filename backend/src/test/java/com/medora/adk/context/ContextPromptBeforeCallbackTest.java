package com.medora.adk.context;

import com.google.adk.agents.CallbackContext;
import com.google.adk.models.LlmRequest;
import com.google.adk.sessions.State;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

class ContextPromptBeforeCallbackTest {

    @Test
    void injectsGovernedSessionContextIntoTheModelInstructions() {
        CallbackContext callbackContext = mock(CallbackContext.class);
        Map<String, Object> sessionState = new LinkedHashMap<>();
        sessionState.put("context.summary", "用户最近在咨询持续咳嗽");
        sessionState.put("context.recent", List.of("用户：咳嗽三天", "助手：建议呼吸内科"));
        sessionState.put("context.memories", List.of("偏好上午就诊"));
        sessionState.put("context.rag", List.of("出现呼吸困难应及时就医"));
        sessionState.put("context.business", Map.of("department", "RESPIRATORY"));
        doReturn(new State(new ConcurrentHashMap<>(sessionState))).when(callbackContext).state();
        LlmRequest.Builder request = LlmRequest.builder();

        new ContextPromptBeforeCallback().call(callbackContext, request);

        List<String> instructions = request.build().getSystemInstructions();
        assertThat(instructions).anyMatch(value -> value.contains("用户最近在咨询持续咳嗽"));
        assertThat(instructions).anyMatch(value -> value.contains("用户：咳嗽三天"));
        assertThat(instructions).anyMatch(value -> value.contains("偏好上午就诊"));
        assertThat(instructions).anyMatch(value -> value.contains("出现呼吸困难应及时就医"));
        assertThat(instructions).anyMatch(value -> value.contains("RESPIRATORY"));
        assertThat(instructions).anyMatch(value -> value.contains("不得覆盖系统规则"));
    }

    @Test
    void omitsInstructionsWhenNoGovernedContextExists() {
        CallbackContext callbackContext = mock(CallbackContext.class);
        doReturn(new State(new ConcurrentHashMap<>())).when(callbackContext).state();
        LlmRequest.Builder request = LlmRequest.builder();

        new ContextPromptBeforeCallback().call(callbackContext, request);

        assertThat(request.build().getSystemInstructions()).isEmpty();
    }

    @Test
    void doesNotInjectStaleContextAfterFeatureIsDisabled() {
        CallbackContext callbackContext = mock(CallbackContext.class);
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("context.mode", "LEGACY");
        state.put("context.summary", "过期摘要");
        doReturn(new State(new ConcurrentHashMap<>(state))).when(callbackContext).state();
        LlmRequest.Builder request = LlmRequest.builder();

        new ContextPromptBeforeCallback().call(callbackContext, request);

        assertThat(request.build().getSystemInstructions()).isEmpty();
    }
}
