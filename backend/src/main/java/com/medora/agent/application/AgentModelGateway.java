package com.medora.agent.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medora.agent.tool.ToolExecutionResult;
import com.medora.agent.tool.ToolNames;
import com.medora.ai.config.AiProperties;
import com.medora.knowledge.service.RetrievalResult;
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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Stream;

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

    public Optional<String> generateReply(String userMessage,
                                          IntentDecision intent,
                                          List<RetrievalResult> retrievalResults,
                                          List<ToolExecutionResult> toolResults) {
        return complete(systemPrompt(), userPrompt(userMessage, intent, retrievalResults, toolResults));
    }

    public Optional<String> generateReplyStreaming(String userMessage,
                                                   IntentDecision intent,
                                                   List<RetrievalResult> retrievalResults,
                                                   List<ToolExecutionResult> toolResults,
                                                   Consumer<String> deltaConsumer) {
        return completeStreaming(systemPrompt(), userPrompt(userMessage, intent, retrievalResults, toolResults), deltaConsumer);
    }

    public Optional<IntentDecision> planIntent(String userMessage) {
        return complete(plannerSystemPrompt(), plannerUserPrompt(userMessage))
                .flatMap(this::parseIntentDecision);
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

    private Optional<String> completeStreaming(String systemPrompt, String userPrompt, Consumer<String> deltaConsumer) {
        String provider = resolveChatProvider();
        if ("openai-compatible".equalsIgnoreCase(provider)) {
            return callOpenAiCompatibleStreaming(systemPrompt, userPrompt, deltaConsumer);
        }
        if ("anthropic-compatible".equalsIgnoreCase(provider)) {
            return callAnthropicCompatibleStreaming(systemPrompt, userPrompt, deltaConsumer);
        }
        log.debug("Agent model streaming skipped because chat provider is local. provider={}", provider);
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

    private Optional<String> callOpenAiCompatibleStreaming(String systemPrompt,
                                                          String userPrompt,
                                                          Consumer<String> deltaConsumer) {
        if (!StringUtils.hasText(resolveApiKey())) {
            return Optional.empty();
        }
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("model", resolveChatModel());
            payload.put("temperature", 0.2);
            payload.put("stream", true);
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
            HttpResponse<Stream<String>> response = httpClient.send(request, HttpResponse.BodyHandlers.ofLines());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("OpenAI-compatible streaming chat call failed. status={}", response.statusCode());
                return Optional.empty();
            }
            StringBuilder reply = new StringBuilder();
            try (Stream<String> lines = response.body()) {
                lines.forEach(line -> appendOpenAiStreamLine(line, reply, deltaConsumer));
            }
            if (!reply.isEmpty()) {
                log.info("OpenAI-compatible streaming chat call succeeded. model={}", resolveChatModel());
                return Optional.of(reply.toString());
            }
            return Optional.empty();
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.warn("OpenAI-compatible streaming chat call failed. baseUrl={}, model={}",
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
                String text = content.path(0).path("text").asText();
                if (StringUtils.hasText(text)) {
                    log.info("Anthropic-compatible chat call succeeded. model={}", resolveChatModel());
                    return Optional.of(text);
                }
            }
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

    private Optional<String> callAnthropicCompatibleStreaming(String systemPrompt,
                                                             String userPrompt,
                                                             Consumer<String> deltaConsumer) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("model", resolveChatModel());
            payload.put("max_tokens", 900);
            payload.put("temperature", 0.2);
            payload.put("stream", true);
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
            HttpResponse<Stream<String>> response = httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofLines());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("Anthropic-compatible streaming chat call failed. status={}", response.statusCode());
                return Optional.empty();
            }
            StringBuilder reply = new StringBuilder();
            try (Stream<String> lines = response.body()) {
                lines.forEach(line -> appendAnthropicStreamLine(line, reply, deltaConsumer));
            }
            if (!reply.isEmpty()) {
                log.info("Anthropic-compatible streaming chat call succeeded. model={}", resolveChatModel());
                return Optional.of(reply.toString());
            }
            return Optional.empty();
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.warn("Anthropic-compatible streaming chat call failed. baseUrl={}, model={}",
                    trimTrailingSlash(resolveBaseUrl()), resolveChatModel(), ex);
            return Optional.empty();
        }
    }

    private String systemPrompt() {
        return """
                你是 Medora 医疗服务助手。你可以解释挂号规则、查询科室医生号源，并基于工具结果协助预约或取消挂号。
                不提供诊断结论，不替代医生面诊。
                业务事实必须以工具结果和知识引用为准，不得编造预约成功、取消成功或号源余量。
                如果工具结果缺少必要参数，要明确告诉用户需要补充什么。
                当 queryScheduleSlots 返回号源时，必须逐条展示 slotId、日期、时段、科室、医生、剩余可约数量；如果用户想预约，只需要让用户回复对应 slotId。
                当 bookAppointment 返回成功时，必须展示 appointmentId、appointmentNo、日期、时段、科室、医生；不要要求用户补充姓名、电话、身份证等当前工具不需要的字段。
                当 cancelAppointment 返回成功时，必须展示 appointmentId、取消状态和取消时间。
                回复要简洁、中文自然，不要输出 Java 对象原始字符串。
                """;
    }

    private String userPrompt(String userMessage,
                              IntentDecision intent,
                              List<RetrievalResult> retrievalResults,
                              List<ToolExecutionResult> toolResults) {
        return toJson(Map.of(
                "userMessage", safe(userMessage),
                "intent", intent,
                "retrievalResults", retrievalResults,
                "toolResults", toolResults
        ));
    }

    private String plannerSystemPrompt() {
        return """
                你是 Medora 的 Agent Planner，只输出 JSON，不输出 Markdown 或解释。
                你的任务是判断用户意图，并选择是否需要知识检索或业务工具。
                可用工具白名单：
                - queryDepartments: 参数 keyword, pageSize。用于查询科室或导诊建议。
                - queryDoctors: 参数 keyword, departmentId, departmentKeyword, pageSize。用于查询医生。
                - queryScheduleSlots: 参数 scheduleDate, periodCode, departmentId, departmentKeyword, doctorId, doctorKeyword, pageSize。用于查询号源，scheduleDate 必须是 yyyy-MM-dd。
                - getUserAppointments: 参数 status, pageSize。用于查询当前用户预约。
                - bookAppointment: 参数 slotId。只有用户明确提供 slotId 时才能调用。
                - cancelAppointment: 参数 appointmentId, reason。只有用户明确提供 appointmentId 时才能调用。
                - searchKnowledge: 参数 query。用于挂号规则、取消规则、院内流程等知识问答。
                输出 JSON schema:
                {
                  "intentType": "QUERY_DEPARTMENTS|QUERY_DOCTORS|QUERY_SCHEDULE_SLOTS|BOOK_APPOINTMENT|CANCEL_APPOINTMENT|KNOWLEDGE_QA|GENERAL_CHAT|...",
                  "routeMode": "CHAT_ONLY|RAG_ONLY|TOOL_ONLY|RAG_AND_TOOL",
                  "needRetrieval": true|false,
                  "toolInvocations": [
                    {"toolName": "queryDepartments", "arguments": {"keyword": "呼吸", "pageSize": 5}}
                  ]
                }
                高风险规则：
                - 不得猜测 slotId 或 appointmentId。
                - 缺少 slotId 时不要预约，可先查询号源。
                - 缺少 appointmentId 时不要取消，可先调用 getUserAppointments 查询 CONFIRMED 预约。
                - 医疗症状导诊只建议科室，不做诊断。
                """;
    }

    private String plannerUserPrompt(String userMessage) {
        return toJson(Map.of(
                "today", LocalDate.now().toString(),
                "userMessage", safe(userMessage)
        ));
    }

    Optional<IntentDecision> parseIntentDecision(String content) {
        try {
            JsonNode root = objectMapper.readTree(extractJsonObject(content));
            String intentType = text(root, "intentType", "GENERAL_CHAT");
            RouteMode routeMode = parseRouteMode(text(root, "routeMode", "CHAT_ONLY"));
            boolean needRetrieval = root.path("needRetrieval").asBoolean(false);
            List<IntentDecision.ToolInvocation> invocations = new ArrayList<>();
            JsonNode tools = root.path("toolInvocations");
            if (tools.isArray()) {
                for (JsonNode tool : tools) {
                    String toolName = text(tool, "toolName", "");
                    if (!isAllowedTool(toolName)) {
                        continue;
                    }
                    Map<String, Object> arguments = objectMapper.convertValue(
                            tool.path("arguments"),
                            new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {
                            }
                    );
                    invocations.add(new IntentDecision.ToolInvocation(toolName, arguments == null ? Map.of() : arguments));
                }
            }
            if (!invocations.isEmpty() && routeMode == RouteMode.CHAT_ONLY) {
                routeMode = needRetrieval ? RouteMode.RAG_AND_TOOL : RouteMode.TOOL_ONLY;
            }
            if (needRetrieval && routeMode == RouteMode.CHAT_ONLY) {
                routeMode = RouteMode.RAG_ONLY;
            }
            return Optional.of(new IntentDecision(intentType, routeMode, needRetrieval, invocations));
        } catch (RuntimeException | IOException ex) {
            return Optional.empty();
        }
    }

    private String extractJsonObject(String content) {
        if (content == null) {
            return "{}";
        }
        int start = content.indexOf('{');
        int end = content.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return content.substring(start, end + 1);
        }
        return content;
    }

    private RouteMode parseRouteMode(String value) {
        try {
            return RouteMode.valueOf(value);
        } catch (RuntimeException ex) {
            return RouteMode.CHAT_ONLY;
        }
    }

    private String text(JsonNode root, String field, String fallback) {
        String value = root.path(field).asText();
        return StringUtils.hasText(value) ? value : fallback;
    }

    private boolean isAllowedTool(String toolName) {
        return ToolNames.QUERY_DEPARTMENTS.equals(toolName)
                || ToolNames.QUERY_DOCTORS.equals(toolName)
                || ToolNames.QUERY_SCHEDULE_SLOTS.equals(toolName)
                || ToolNames.GET_USER_APPOINTMENTS.equals(toolName)
                || ToolNames.BOOK_APPOINTMENT.equals(toolName)
                || ToolNames.CANCEL_APPOINTMENT.equals(toolName)
                || ToolNames.SEARCH_KNOWLEDGE.equals(toolName);
    }

    private void appendOpenAiStreamLine(String line, StringBuilder reply, Consumer<String> deltaConsumer) {
        String payload = sseDataPayload(line);
        if (!StringUtils.hasText(payload) || "[DONE]".equals(payload)) {
            return;
        }
        try {
            JsonNode content = objectMapper.readTree(payload)
                    .path("choices")
                    .path(0)
                    .path("delta")
                    .path("content");
            appendDelta(content.asText(), reply, deltaConsumer);
        } catch (IOException ex) {
            log.debug("Ignored malformed OpenAI-compatible stream line: {}", snippet(payload), ex);
        }
    }

    private void appendAnthropicStreamLine(String line, StringBuilder reply, Consumer<String> deltaConsumer) {
        String payload = sseDataPayload(line);
        if (!StringUtils.hasText(payload)) {
            return;
        }
        try {
            JsonNode root = objectMapper.readTree(payload);
            String type = root.path("type").asText();
            if ("content_block_delta".equals(type)) {
                appendDelta(root.path("delta").path("text").asText(), reply, deltaConsumer);
            }
            if ("content_block_start".equals(type)) {
                appendDelta(root.path("content_block").path("text").asText(), reply, deltaConsumer);
            }
        } catch (IOException ex) {
            log.debug("Ignored malformed Anthropic-compatible stream line: {}", snippet(payload), ex);
        }
    }

    private String sseDataPayload(String line) {
        if (line == null) {
            return "";
        }
        String trimmed = line.trim();
        if (!trimmed.startsWith("data:")) {
            return "";
        }
        return trimmed.substring("data:".length()).trim();
    }

    private void appendDelta(String delta, StringBuilder reply, Consumer<String> deltaConsumer) {
        if (!StringUtils.hasText(delta)) {
            return;
        }
        reply.append(delta);
        deltaConsumer.accept(delta);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (IOException ex) {
            return "{}";
        }
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String resolveChatProvider() {
        String provider = aiProperties.getChatProvider();
        if (StringUtils.hasText(provider) && !"local".equalsIgnoreCase(provider)) {
            return provider;
        }
        if (StringUtils.hasText(System.getenv("ANTHROPIC_BASE_URL"))) {
            return "anthropic-compatible";
        }
        return StringUtils.hasText(provider) ? provider : "local";
    }

    private String resolveApiKey() {
        if (StringUtils.hasText(aiProperties.getApiKey())) {
            return aiProperties.getApiKey();
        }
        String anthropicKey = System.getenv("ANTHROPIC_API_KEY");
        return anthropicKey == null ? "" : anthropicKey;
    }

    private String resolveBaseUrl() {
        String anthropicBaseUrl = System.getenv("ANTHROPIC_BASE_URL");
        if (StringUtils.hasText(anthropicBaseUrl) && "anthropic-compatible".equalsIgnoreCase(resolveChatProvider())) {
            return anthropicBaseUrl;
        }
        return aiProperties.getBaseUrl();
    }

    private String resolveChatModel() {
        String anthropicModel = System.getenv("ANTHROPIC_MODEL");
        if (StringUtils.hasText(anthropicModel) && "anthropic-compatible".equalsIgnoreCase(resolveChatProvider())) {
            return anthropicModel;
        }
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
