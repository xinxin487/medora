package com.medora.eval.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Phase 10 W5.5 — OpenAiCompatibleLlmClient 单测。
 *
 * <p>用 JDK 自带 {@link com.sun.net.httpserver.HttpServer} 起一个 in-process stub,
 * 验证:
 * <ul>
 *   <li>请求体 OpenAI-compatible schema:model / temperature=0 / messages[role,content]</li>
 *   <li>URL 是 baseUrl + "/chat/completions",Authorization Bearer 头</li>
 *   <li>响应解析提取 choices[0].message.content</li>
 *   <li>非 2xx / 空 choices / 空 content 都抛异常</li>
 * </ul>
 *
 * <p>不依赖外部网络,CI 安全。
 */
class OpenAiCompatibleLlmClientTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void postsOpenAiCompatibleBodyAndParsesChoicesContent() throws Exception {
        AtomicReference<String> capturedBody = new AtomicReference<>();
        AtomicReference<String> capturedAuth = new AtomicReference<>();
        AtomicReference<String> capturedPath = new AtomicReference<>();
        HttpServer server = startServer(ex -> {
            capturedPath.set(ex.getRequestURI().getPath());
            capturedAuth.set(ex.getRequestHeaders().getFirst("Authorization"));
            capturedBody.set(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            String resp = """
                    {"choices":[{"message":{"role":"assistant","content":"已为您找到 3 个呼吸内科医生"}}]}
                    """;
            writeJson(ex, 200, resp);
        });

        try {
            String baseUrl = "http://localhost:" + server.getAddress().getPort() + "/v1";
            OpenAiCompatibleLlmClient client = new OpenAiCompatibleLlmClient(baseUrl, "sk-test-key", "deepseek-chat");
            String resp = client.complete("你是医疗助手", "查呼吸内科医生");

            assertThat(resp).isEqualTo("已为您找到 3 个呼吸内科医生");
            assertThat(capturedPath.get()).isEqualTo("/v1/chat/completions");
            assertThat(capturedAuth.get()).isEqualTo("Bearer sk-test-key");

            JsonNode body = MAPPER.readTree(capturedBody.get());
            assertThat(body.path("model").asText()).isEqualTo("deepseek-chat");
            assertThat(body.path("temperature").asDouble()).isEqualTo(0.0);
            assertThat(body.path("messages").isArray()).isTrue();
            assertThat(body.path("messages").size()).isEqualTo(2);
            assertThat(body.path("messages").get(0).path("role").asText()).isEqualTo("system");
            assertThat(body.path("messages").get(0).path("content").asText()).isEqualTo("你是医疗助手");
            assertThat(body.path("messages").get(1).path("role").asText()).isEqualTo("user");
            assertThat(body.path("messages").get(1).path("content").asText()).isEqualTo("查呼吸内科医生");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void omitsSystemMessageWhenBlank() throws Exception {
        AtomicReference<String> capturedBody = new AtomicReference<>();
        HttpServer server = startServer(ex -> {
            capturedBody.set(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            writeJson(ex, 200, "{\"choices\":[{\"message\":{\"content\":\"ok\"}}]}");
        });
        try {
            new OpenAiCompatibleLlmClient("http://localhost:" + server.getAddress().getPort(), "k", "m")
                    .complete(null, "hi");
            JsonNode body = MAPPER.readTree(capturedBody.get());
            assertThat(body.path("messages").size()).isEqualTo(1);
            assertThat(body.path("messages").get(0).path("role").asText()).isEqualTo("user");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void throwsOnNon2xxStatus() throws Exception {
        HttpServer server = startServer(ex -> writeJson(ex, 401, "{\"error\":\"bad key\"}"));
        try {
            OpenAiCompatibleLlmClient client = new OpenAiCompatibleLlmClient(
                    "http://localhost:" + server.getAddress().getPort(), "k", "m");
            assertThatThrownBy(() -> client.complete("sys", "usr"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("HTTP 401");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void throwsWhenResponseHasNoChoices() throws Exception {
        HttpServer server = startServer(ex -> writeJson(ex, 200, "{\"choices\":[]}"));
        try {
            OpenAiCompatibleLlmClient client = new OpenAiCompatibleLlmClient(
                    "http://localhost:" + server.getAddress().getPort(), "k", "m");
            assertThatThrownBy(() -> client.complete("sys", "usr"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("no choices");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void describeIncludesBaseUrlAndModel() {
        OpenAiCompatibleLlmClient client = new OpenAiCompatibleLlmClient(
                "https://api.deepseek.com/v1/", "k", "deepseek-chat");
        // trailing slash 在构造时被剥掉
        assertThat(client.describe()).isEqualTo("openai-compatible:https://api.deepseek.com/v1:deepseek-chat");
    }

    private static HttpServer startServer(HttpHandler handler) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", handler);
        server.start();
        return server;
    }

    private static void writeJson(HttpExchange ex, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(status, bytes.length);
        try (var os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }
}
