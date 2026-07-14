package com.medora.eval.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Phase 10 W5.5 — 真实 OpenAI-compatible LLM 客户端(评测 record 模式用)。
 *
 * <p>用于 --mode=record:调用真实 LLM(DeepSeek / OpenAI / 任何 OpenAI 兼容端点),
 * 把响应交给 {@link RecordingLlmClient} 落盘成 recordings.json。
 *
 * <p>为什么放在 test scope:评测专用,不进生产 jar。生产代码的 LLM 调用走 AgentModelGateway,
 * 评测链路不能因为生产代码的演进被绑架。
 *
 * <p>安全:API key 只通过构造器注入(由 EvalCli 从环境变量读取),不进日志。
 */
public class OpenAiCompatibleLlmClient implements LlmClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String baseUrl;
    private final String apiKey;
    private final String model;
    private final HttpClient http;
    private final Duration timeout;

    public OpenAiCompatibleLlmClient(String baseUrl, String apiKey, String model) {
        this(baseUrl, apiKey, model, Duration.ofSeconds(60));
    }

    public OpenAiCompatibleLlmClient(String baseUrl, String apiKey, String model, Duration timeout) {
        this.baseUrl = stripTrailingSlash(baseUrl);
        this.apiKey = apiKey;
        this.model = model;
        this.timeout = timeout;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Override
    public String complete(String systemPrompt, String userPrompt) throws Exception {
        ObjectNode body = MAPPER.createObjectNode();
        body.put("model", model);
        body.put("temperature", 0);
        ArrayNode messages = body.putArray("messages");
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            ObjectNode sys = messages.addObject();
            sys.put("role", "system");
            sys.put("content", systemPrompt);
        }
        ObjectNode user = messages.addObject();
        user.put("role", "user");
        user.put("content", userPrompt);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/chat/completions"))
                .timeout(timeout)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(body)))
                .build();

        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() / 100 != 2) {
            throw new IllegalStateException("LLM HTTP " + resp.statusCode()
                    + " from " + baseUrl + " model=" + model
                    + " body=" + snippet(resp.body()));
        }
        JsonNode root = MAPPER.readTree(resp.body());
        JsonNode choices = root.path("choices");
        if (!choices.isArray() || choices.isEmpty()) {
            throw new IllegalStateException("LLM response has no choices: " + snippet(resp.body()));
        }
        String content = choices.get(0).path("message").path("content").asText("");
        if (content.isBlank()) {
            throw new IllegalStateException("LLM response content empty: " + snippet(resp.body()));
        }
        return content;
    }

    @Override
    public String describe() {
        return "openai-compatible:" + baseUrl + ":" + model;
    }

    private static String stripTrailingSlash(String url) {
        if (url == null) throw new IllegalArgumentException("baseUrl is null");
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private static String snippet(String s) {
        if (s == null) return "(null)";
        return s.length() <= 200 ? s : s.substring(0, 200) + "...";
    }
}
