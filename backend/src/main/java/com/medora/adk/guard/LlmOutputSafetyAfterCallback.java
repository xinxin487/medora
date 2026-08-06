package com.medora.adk.guard;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.adk.agents.Callbacks.AfterModelCallbackSync;
import com.google.adk.agents.CallbackContext;
import com.google.adk.models.LlmResponse;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * AfterModelCallback —— 输出安全审查,挂在 RootAgent。
 *
 * 决策层 LLM-only:不再使用 OutputSafetyGuard 的正则关键词列表(诊断性语言等),
 * 改为纯 LLM 判断。LLM 不可用 → 抛 LlmUnavailableException(符合 LLM-first)。
 *
 * LLM 判定输出包含:
 *  - hasDiagnostic:对用户做出疾病诊断
 *  - hasMedicationAdvice:推荐具体药物
 *  - hasSystemLeak:泄露系统内部信息
 *
 * 命中时:不替换回复(避免双重对话流),而是在 LlmResponse 后追加医疗免责声明。
 */
@Component
public class LlmOutputSafetyAfterCallback implements AfterModelCallbackSync {

    private static final Logger log = LoggerFactory.getLogger(LlmOutputSafetyAfterCallback.class);

    public static final String DISCLAIMER = "\n\n—— 以上内容仅供参考,不构成医疗诊断,如有疑问请咨询医生。";

    private static final String OUTPUT_SAFETY_PROMPT = """
            你是 Medora 医疗助手的输出安全审查模块。请检查助手回复是否包含以下问题:
            1. 医疗诊断:是否对患者做出了疾病诊断(如"你患有XX"、"确诊为XX")
            2. 用药建议:是否推荐了具体药物(如"吃XX药"、"服用XX"),注意区分"建议就医"和"推荐药物"
            3. 系统信息泄露:是否暴露了内部系统信息(工具名称、内部 ID、代码、异常堆栈)

            只输出严格 JSON,不要任何其他内容:
            {"safe": true/false, "hasDiagnostic": true/false, "hasMedicationAdvice": true/false, "hasSystemLeak": true/false, "reason": "原因"}
            """;

    private final LlmSafetyClient llmSafetyClient;
    private final ObjectMapper objectMapper;

    public LlmOutputSafetyAfterCallback(LlmSafetyClient llmSafetyClient, ObjectMapper objectMapper) {
        this.llmSafetyClient = llmSafetyClient;
        this.objectMapper = objectMapper;
    }

    public SafetyDecision check(String reply) {
        if (reply == null || reply.isBlank()) {
            return SafetyDecision.asSafe();
        }
        String llmResponse = llmSafetyClient.call("outputSafetyCheck",
                OUTPUT_SAFETY_PROMPT, "请审查:\n" + reply);
        try {
            JsonNode root = objectMapper.readTree(llmResponse);
            boolean safe = root.path("safe").asBoolean(true);
            boolean diagnostic = root.path("hasDiagnostic").asBoolean(false);
            boolean medication = root.path("hasMedicationAdvice").asBoolean(false);
            boolean leak = root.path("hasSystemLeak").asBoolean(false);
            if (safe && !diagnostic && !medication && !leak) {
                return SafetyDecision.asSafe();
            }
            String reason = root.path("reason").asText("LLM 判定输出不安全");
            OutputAction action = leak ? OutputAction.REPLACE_REPLY : OutputAction.APPEND_DISCLAIMER;
            log.warn("OutputSafety LLM flagged. diagnostic={}, medication={}, leak={}, reason={}",
                    diagnostic, medication, leak, reason);
            return SafetyDecision.unsafe(action, reason);
        } catch (Exception e) {
            log.warn("OutputSafety LLM response not valid JSON, treating as unsafe (replace). response={}",
                    snippet(llmResponse));
            return SafetyDecision.unsafe(OutputAction.REPLACE_REPLY,
                    "LLM 输出安全审查响应不可解析");
        }
    }

    @Override
    public Optional<LlmResponse> call(CallbackContext callbackContext, LlmResponse llmResponse) {
        String reply = extractReply(llmResponse);
        SafetyDecision decision = check(reply);
        if (decision.safe()) {
            return Optional.empty();
        }
        if (decision.action() == OutputAction.APPEND_DISCLAIMER) {
            return Optional.of(appendDisclaimer(llmResponse));
        }
        return Optional.of(replaceReply());
    }

    private static LlmResponse appendDisclaimer(LlmResponse original) {
        String originalText = extractReply(original);
        String newText = originalText + DISCLAIMER;
        return LlmResponse.builder()
                .content(Content.builder()
                        .role("model")
                        .parts(List.of(Part.builder().text(newText).build()))
                        .build())
                .turnComplete(original.turnComplete().orElse(true))
                .build();
    }

    private static LlmResponse replaceReply() {
        // 系统信息泄露时直接替换为静态消息,不暴露任何泄露内容
        return LlmResponse.builder()
                .content(Content.builder()
                        .role("model")
                        .parts(List.of(Part.builder()
                                .text("抱歉,本次回复无法显示,请重新发起咨询。")
                                .build()))
                        .build())
                .turnComplete(true)
                .build();
    }

    private static String extractReply(LlmResponse llmResponse) {
        return llmResponse.content()
                .flatMap(Content::parts)
                .map(parts -> parts.stream()
                        .map(Part::text)
                        .filter(Optional::isPresent)
                        .map(Optional::get)
                        .reduce("", String::concat))
                .orElse("");
    }

    private static String snippet(String text) {
        if (text == null) return "";
        return text.length() <= 100 ? text : text.substring(0, 100);
    }

    public enum OutputAction {
        NONE,
        APPEND_DISCLAIMER,
        REPLACE_REPLY
    }

    public record SafetyDecision(boolean safe, OutputAction action, String reason) {
        public static SafetyDecision asSafe() {
            return new SafetyDecision(true, OutputAction.NONE, null);
        }

        public static SafetyDecision unsafe(OutputAction action, String reason) {
            return new SafetyDecision(false, action, reason);
        }
    }
}
