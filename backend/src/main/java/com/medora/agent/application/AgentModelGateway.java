package com.medora.agent.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medora.ai.config.AiProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Phase 9.8 — 收缩后的 LLM 网关,仅保留 ADK 之外仍需要的 LLM 调用。
 *
 * <p>历史:Phase 0-9.7 期间本类承担意图规划、流式回复、幻觉评估、医疗建议检测等职责。
 * Phase 9.8 起,这些职责全部由 ADK 内部 callback + agent 拓扑接管:
 * <ul>
 *   <li>意图规划 → RootRouterAgent(LLM 路由)</li>
 *   <li>幻觉评估 → LlmHallucinationAfterCallback</li>
 *   <li>医疗免责 → LlmOutputSafetyAfterCallback</li>
 *   <li>流式回复 → ADK Runner 内部</li>
 * </ul>
 *
 * <p>现存职责:为非 ADK 调用方(MemoryService / ConversationSummaryService /
 * SafetyGuard / OutputSafetyGuard / LlmSafetyClient / AdkEventToSseAdapter fallback)
 * 提供统一的轻量级 LLM 入口。
 */
@Component
public class AgentModelGateway {

    private static final Logger log = LoggerFactory.getLogger(AgentModelGateway.class);
    private static final Pattern HTTPS_IP_BASE_URL = Pattern.compile("^https://\\d{1,3}(?:\\.\\d{1,3}){3}(?::\\d+)?(?:/.*)?$");

    private final AiProperties aiProperties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public AgentModelGateway(AiProperties aiProperties, ObjectMapper objectMapper) {
        this.aiProperties = aiProperties;
        this.objectMapper = objectMapper;
        this.httpClient = buildHttpClient();
    }

    /**
     * 轻量级 LLM 调用 — 用于安全审查、记忆提取、对话摘要、幻觉评估等场景。
     * 使用低温度和短 max_tokens,追求快速响应。
     */
    public Optional<String> lightweightLlmCall(String systemPrompt, String userPrompt) {
        return complete(systemPrompt, userPrompt);
    }

    /**
     * LLM 智能记忆提取 — 从用户消息中提取结构化偏好记忆。
     */
    public Optional<String> extractMemories(String userMessage) {
        String systemPrompt = """
                你是 Medora 医疗助手的记忆提取模块。从用户消息中提取用户的偏好信息。
                只提取明确表达的偏好，不要推测。

                输出 JSON 数组（如果没有提取到任何偏好，输出空数组 []）：
                [
                  {"memoryType": "PREFERRED_DEPARTMENT", "memoryKey": "科室名", "summary": "用户偏好XX科", "confidence": 0.8},
                  {"memoryType": "PREFERRED_DOCTOR", "memoryKey": "医生名", "summary": "用户偏好XX医生", "confidence": 0.8},
                  {"memoryType": "PREFERRED_PERIOD", "memoryKey": "AM|PM", "summary": "用户偏好上午/下午号源", "confidence": 0.8}
                ]

                提取规则：
                - PREFERRED_DEPARTMENT：用户提到偏好某个科室（如"我一般看内科"、"帮我挂心内科"、"想去皮肤科"）
                - PREFERRED_DOCTOR：用户提到偏好某个医生（如"张医生"、"王主任"、"李大夫"）
                - PREFERRED_PERIOD：用户提到偏好某个时段（如"上午方便"、"习惯下午去"、"不想到晚上"、"一般早上来"）
                - confidence: 0.3（模糊提及）到 0.9（明确偏好）
                - 不要提取疑问句中的科室/医生（如"看什么科好"不提取）
                - 只输出 JSON，不要输出任何解释。
                """;
        return lightweightLlmCall(systemPrompt, userMessage);
    }

    /**
     * LLM 生成对话摘要 — 提取主题、确认事实和待办事项。
     */
    public Optional<String> generateConversationSummary(String userMessage, String toolResultsSummary) {
        String systemPrompt = """
                你是 Medora 医疗助手的对话摘要模块。根据用户消息和工具执行结果，生成结构化摘要。

                只输出 JSON：
                {
                  "topic": "简短主题描述（10字以内）",
                  "confirmedFacts": ["确认的事实1", "确认的事实2"],
                  "pendingTasks": ["待办事项1"]
                }

                规则：
                - topic 要具体（如"号源查询"、"预约办理"、"科室导诊"），不要用"一般咨询"
                - confirmedFacts 只提取已确认的信息，不要推测
                - pendingTasks 只提取真正待办的事项
                - 最多 4 个 confirmedFacts 和 3 个 pendingTasks
                - 只输出 JSON，不要输出任何解释。
                """;
        String userPrompt = "用户消息：" + userMessage + "\n\n工具结果：" + toolResultsSummary;
        return lightweightLlmCall(systemPrompt, userPrompt);
    }

    private Optional<String> complete(String systemPrompt, String userPrompt) {
        String provider = resolveChatProvider();
        if ("openai-compatible".equalsIgnoreCase(provider)) {
            return callOpenAiCompatible(systemPrompt, userPrompt);
        }
        if ("anthropic-compatible".equalsIgnoreCase(provider)) {
            return callAnthropicCompatible(systemPrompt, userPrompt);
        }
        log.debug("Agent model call skipped because chat provider is local. provider={}", provider);
        return Optional.empty();
    }

    private Optional<String> callOpenAiCompatible(String systemPrompt, String userPrompt) {
        if (!StringUtils.hasText(resolveApiKey())) {
            return Optional.empty();
        }
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("model", resolveChatModel());
            payload.put("temperature", 0.2);
            payload.put("messages", List.of(
                    Map.of("role", "system", "content", systemPrompt),
                    Map.of("role", "user", "content", userPrompt)
            ));
            String body = objectMapper.writeValueAsString(payload);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(trimTrailingSlash(resolveBaseUrl()) + "/chat/completions"))
                    .timeout(Duration.ofSeconds(60))
                    .header("Authorization", "Bearer " + resolveApiKey())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("OpenAI-compatible chat call failed. status={}, body={}",
                        response.statusCode(), snippet(response.body()));
                return Optional.empty();
            }
            JsonNode content = objectMapper.readTree(response.body()).path("choices").path(0).path("message").path("content");
            if (StringUtils.hasText(content.asText())) {
                log.info("OpenAI-compatible chat call succeeded. model={}", resolveChatModel());
                return Optional.of(content.asText());
            }
            return Optional.empty();
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.warn("OpenAI-compatible chat call failed. baseUrl={}, model={}",
                    trimTrailingSlash(resolveBaseUrl()), resolveChatModel(), ex);
            return Optional.empty();
        }
    }

    private Optional<String> callAnthropicCompatible(String systemPrompt, String userPrompt) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("model", resolveChatModel());
            payload.put("max_tokens", 900);
            payload.put("temperature", 0.2);
            payload.put("system", systemPrompt);
            payload.put("messages", List.of(Map.of(
                    "role", "user",
                    "content", userPrompt
            )));
            String body = objectMapper.writeValueAsString(payload);
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(toAnthropicMessagesUrl(resolveBaseUrl())))
                    .timeout(Duration.ofSeconds(60))
                    .header("anthropic-version", "2023-06-01")
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
            if (StringUtils.hasText(resolveApiKey())) {
                requestBuilder.header("x-api-key", resolveApiKey());
            }
            HttpResponse<String> response = httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("Anthropic-compatible chat call failed. status={}, body={}",
                        response.statusCode(), snippet(response.body()));
                return Optional.empty();
            }
            JsonNode content = objectMapper.readTree(response.body()).path("content");
            if (content.isArray() && !content.isEmpty()) {
                // DeepSeek thinking 模型：content 数组包含 thinking 块和 text 块，需要找到 text 块
                for (JsonNode block : content) {
                    if ("text".equals(block.path("type").asText())) {
                        String text = block.path("text").asText();
                        if (StringUtils.hasText(text)) {
                            log.info("Anthropic-compatible chat call succeeded. model={}", resolveChatModel());
                            return Optional.of(text);
                        }
                    }
                }
            }
            log.warn("Anthropic-compatible chat call returned unexpected body. url={}, body={}",
                    toAnthropicMessagesUrl(resolveBaseUrl()), snippet(response.body()));
            return Optional.empty();
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.warn("Anthropic-compatible chat call failed. baseUrl={}, model={}",
                    trimTrailingSlash(resolveBaseUrl()), resolveChatModel(), ex);
            return Optional.empty();
        }
    }

    private String resolveChatProvider() {
        String provider = aiProperties.getChatProvider();
        if (StringUtils.hasText(provider) && !"local".equalsIgnoreCase(provider)) {
            return provider;
        }
        if (StringUtils.hasText(aiProperties.getChatBaseUrl())) {
            return "anthropic-compatible";
        }
        return StringUtils.hasText(provider) ? provider : "local";
    }

    private String resolveApiKey() {
        if (StringUtils.hasText(aiProperties.getChatApiKey())) {
            return aiProperties.getChatApiKey();
        }
        return aiProperties.getApiKey();
    }

    private String resolveBaseUrl() {
        if (StringUtils.hasText(aiProperties.getChatBaseUrl())) {
            return aiProperties.getChatBaseUrl();
        }
        return aiProperties.getBaseUrl();
    }

    private String resolveChatModel() {
        return aiProperties.getChatModel();
    }

    private HttpClient buildHttpClient() {
        HttpClient.Builder builder = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10));
        if (!shouldUseInsecureTls()) {
            return builder.build();
        }

        try {
            System.setProperty("jdk.internal.httpclient.disableHostnameVerification", "true");
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, new TrustManager[]{trustAllManager()}, new SecureRandom());
            SSLParameters sslParameters = new SSLParameters();
            sslParameters.setEndpointIdentificationAlgorithm("");
            log.warn("Agent model gateway is using insecure TLS for dev model proxy. baseUrl={}",
                    trimTrailingSlash(resolveBaseUrl()));
            return builder.sslContext(sslContext)
                    .sslParameters(sslParameters)
                    .build();
        } catch (Exception ex) {
            log.warn("Failed to initialize insecure TLS client; falling back to default TLS validation.", ex);
            return builder.build();
        }
    }

    private boolean shouldUseInsecureTls() {
        return aiProperties.isInsecureTls() || HTTPS_IP_BASE_URL.matcher(trimTrailingSlash(resolveBaseUrl())).matches();
    }

    private X509TrustManager trustAllManager() {
        return new X509TrustManager() {
            @Override
            public void checkClientTrusted(X509Certificate[] chain, String authType) {
            }

            @Override
            public void checkServerTrusted(X509Certificate[] chain, String authType) {
            }

            @Override
            public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[0];
            }
        };
    }

    private String trimTrailingSlash(String value) {
        String normalized = StringUtils.hasText(value) ? value.trim() : "https://api.openai.com/v1";
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private String toAnthropicMessagesUrl(String baseUrl) {
        String normalized = trimTrailingSlash(baseUrl);
        return normalized.endsWith("/v1") ? normalized + "/messages" : normalized + "/v1/messages";
    }

    private String snippet(String body) {
        if (body == null) {
            return "";
        }
        String normalized = body.replaceAll("\\s+", " ").trim();
        return normalized.length() <= 500 ? normalized : normalized.substring(0, 500);
    }
}
