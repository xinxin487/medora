package com.medora.eval.llm;

/**
 * Phase 10 W4 测试桩 — LlmClient 的固定响应实现。
 *
 * <p>按 (systemPrompt + userPrompt) 的精确匹配返回预设 response;
 * 用于 LlmJudge / AdversarialCaseGenerator 单测。
 */
public class StubLlmClient implements LlmClient {

    private final String fixedResponse;

    public StubLlmClient(String fixedResponse) {
        this.fixedResponse = fixedResponse;
    }

    @Override
    public String complete(String systemPrompt, String userPrompt) {
        return fixedResponse;
    }

    @Override
    public String describe() {
        return "stub";
    }
}
