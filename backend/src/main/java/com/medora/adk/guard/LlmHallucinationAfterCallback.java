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
 * AfterModelCallback —— 幻觉检测,挂在 KnowledgeAgent(RAG 问答场景)。
 *
 * 决策层 LLM-only:删除原 hallucinationRisk() 的所有规则分支(ChatOrchestrator:336-347),
 * 改为纯 LLM 评分。
 *
 * 阈值:score < 0.6 视为高风险。LLM 不可用 → 抛 LlmUnavailableException,
 * 不允许「无评分就放行」。
 *
 * 命中高风险时:替换 LlmResponse 为「无法验证」提示,而非直接展示可能有幻觉的回复。
 */
@Component
public class LlmHallucinationAfterCallback implements AfterModelCallbackSync {

    private static final Logger log = LoggerFactory.getLogger(LlmHallucinationAfterCallback.class);

    public static final double SCORE_THRESHOLD = 0.6;

    private static final String HALLUCINATION_PROMPT = """
            你是 Medora 医疗助手的幻觉检测模块。请评估助手回复相对于知识库引用的可信度。

            判断依据:
            - 每个事实陈述是否能在引用文本中找到支撑
            - 是否存在编造的数字、药名、症状、操作步骤
            - 是否存在与引用冲突的陈述

            只输出严格 JSON,不要任何其他内容:
            {"score": 0.0-1.0, "unsupported_claims": ["无法验证的陈述"], "reason": "评分依据"}
            score=1.0 表示完全有引用支撑,score<0.6 表示高风险幻觉。
            """;

    private final LlmSafetyClient llmSafetyClient;
    private final ObjectMapper objectMapper;

    public LlmHallucinationAfterCallback(LlmSafetyClient llmSafetyClient, ObjectMapper objectMapper) {
        this.llmSafetyClient = llmSafetyClient;
        this.objectMapper = objectMapper;
    }

    public HallucinationScore score(String reply, String citationContext) {
        String userPrompt = "回复:\n" + reply + "\n\n引用文本:\n" + citationContext;
        String llmResponse = llmSafetyClient.call("hallucinationCheck",
                HALLUCINATION_PROMPT, userPrompt);
        try {
            JsonNode root = objectMapper.readTree(llmResponse);
            double score = root.path("score").asDouble(1.0);
            List<String> claims = objectMapper.convertValue(
                    root.path("unsupported_claims"), List.class);
            String reason = root.path("reason").asText("");
            return new HallucinationScore(score, claims == null ? List.of() : claims, reason);
        } catch (Exception e) {
            // 解析失败视为低分(保守)
            log.warn("Hallucination LLM response not valid JSON, defaulting to score=0. response={}",
                    snippet(llmResponse));
            return new HallucinationScore(0.0, List.of(), "LLM 响应不可解析");
        }
    }

    @Override
    public Optional<LlmResponse> call(CallbackContext callbackContext, LlmResponse llmResponse) {
        String reply = extractReply(llmResponse);
        if (reply.isBlank()) {
            return Optional.empty();
        }
        // CitationContext 从 session state 取 — Phase 9.5 KnowledgeAgent 装配时写入
        // Phase 9.4 测试只验证 score() 方法,callback 接入留 9.5
        String citationContext = (String) callbackContext.state().getOrDefault(
                "medora:lastRetrievalContext", "");
        if (citationContext.isBlank()) {
            // 无引用上下文意味着上一轮没有 RAG,幻觉检测不适用
            return Optional.empty();
        }
        HallucinationScore score = score(reply, citationContext);
        if (score.score() >= SCORE_THRESHOLD) {
            return Optional.empty();
        }
        log.warn("Hallucination detected. score={}, unsupportedClaims={}",
                score.score(), score.unsupportedClaims());
        return Optional.of(replaceReply(score));
    }

    private static LlmResponse replaceReply(HallucinationScore score) {
        return LlmResponse.builder()
                .content(Content.builder()
                        .role("model")
                        .parts(List.of(Part.builder()
                                .text("抱歉,本次回复未能从知识库中找到充分支撑,"
                                        + "建议您咨询专业医生或换个角度提问。")
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

    public record HallucinationScore(double score, List<String> unsupportedClaims, String reason) {
        public boolean highRisk() {
            return score < SCORE_THRESHOLD;
        }
    }
}
