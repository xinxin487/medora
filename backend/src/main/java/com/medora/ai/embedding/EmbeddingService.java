package com.medora.ai.embedding;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medora.ai.config.AiProperties;
import com.medora.common.exception.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class EmbeddingService {

    private final AiProperties aiProperties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public EmbeddingService(AiProperties aiProperties, ObjectMapper objectMapper) {
        this.aiProperties = aiProperties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public EmbeddingVector embed(String input) {
        String provider = aiProperties.getProvider() == null ? "local-hash" : aiProperties.getProvider();
        if ("openai-compatible".equalsIgnoreCase(provider)) {
            return embedByOpenAiCompatible(input);
        }
        return embedByLocalHash(input);
    }

    private EmbeddingVector embedByOpenAiCompatible(String input) {
        if (!StringUtils.hasText(aiProperties.getApiKey())) {
            throw new BusinessException("AI_API_KEY_MISSING", HttpStatus.UNPROCESSABLE_ENTITY,
                    "embedding api key is required");
        }
        try {
            String baseUrl = trimTrailingSlash(aiProperties.getBaseUrl());
            String body = objectMapper.writeValueAsString(buildEmbeddingRequest(input));
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/embeddings"))
                    .timeout(Duration.ofSeconds(60))
                    .header("Authorization", "Bearer " + aiProperties.getApiKey())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new BusinessException("AI_EMBEDDING_FAILED", HttpStatus.BAD_GATEWAY,
                        "embedding request failed: " + response.statusCode());
            }
            JsonNode embedding = objectMapper.readTree(response.body()).path("data").path(0).path("embedding");
            if (!embedding.isArray()) {
                throw new BusinessException("AI_EMBEDDING_FAILED", HttpStatus.BAD_GATEWAY,
                        "embedding response is invalid");
            }
            double[] values = new double[embedding.size()];
            for (int index = 0; index < embedding.size(); index++) {
                values[index] = embedding.get(index).asDouble();
            }
            validateDimensions(values);
            return new EmbeddingVector(normalize(values), aiProperties.getEmbeddingModel());
        } catch (IOException ex) {
            throw new BusinessException("AI_EMBEDDING_FAILED", HttpStatus.BAD_GATEWAY,
                    "embedding request failed");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new BusinessException("AI_EMBEDDING_FAILED", HttpStatus.BAD_GATEWAY,
                    "embedding request interrupted");
        }
    }

    private Map<String, Object> buildEmbeddingRequest(String input) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", aiProperties.getEmbeddingModel());
        payload.put("input", input);
        if (supportsDimensionsOverride(aiProperties.getEmbeddingModel(), aiProperties.getEmbeddingDimensions())) {
            payload.put("dimensions", aiProperties.getEmbeddingDimensions());
        }
        return payload;
    }

    private boolean supportsDimensionsOverride(String model, int dimensions) {
        if (dimensions <= 0 || !StringUtils.hasText(model)) {
            return false;
        }
        return model.startsWith("Qwen/Qwen3-Embedding-")
                || model.startsWith("text-embedding-3");
    }

    private EmbeddingVector embedByLocalHash(String input) {
        int dimensions = Math.max(aiProperties.getEmbeddingDimensions(), 8);
        double[] values = new double[dimensions];
        for (String token : tokenize(input)) {
            byte[] digest = sha256(token);
            int bucket = Math.floorMod(toInt(digest), dimensions);
            double sign = (digest[4] & 1) == 0 ? 1.0 : -1.0;
            values[bucket] += sign;
        }
        return new EmbeddingVector(normalize(values), "local-hash-" + dimensions);
    }

    private void validateDimensions(double[] values) {
        int expected = aiProperties.getEmbeddingDimensions();
        if (expected > 0 && values.length != expected) {
            throw new BusinessException("AI_EMBEDDING_DIMENSION_MISMATCH", HttpStatus.UNPROCESSABLE_ENTITY,
                    "embedding dimensions mismatch, expected " + expected + " but got " + values.length);
        }
    }

    private List<String> tokenize(String input) {
        String normalized = input == null ? "" : input.toLowerCase().replaceAll("\\s+", "");
        if (normalized.isBlank()) {
            return List.of();
        }
        java.util.ArrayList<String> tokens = new java.util.ArrayList<>();
        for (int index = 0; index < normalized.length(); index++) {
            tokens.add(normalized.substring(index, index + 1));
            if (index + 2 <= normalized.length()) {
                tokens.add(normalized.substring(index, index + 2));
            }
        }
        return tokens;
    }

    private byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not supported", ex);
        }
    }

    private int toInt(byte[] digest) {
        return ((digest[0] & 0xff) << 24)
                | ((digest[1] & 0xff) << 16)
                | ((digest[2] & 0xff) << 8)
                | (digest[3] & 0xff);
    }

    private double[] normalize(double[] values) {
        double norm = 0.0;
        for (double value : values) {
            norm += value * value;
        }
        if (norm == 0.0) {
            return values;
        }
        double scale = Math.sqrt(norm);
        for (int index = 0; index < values.length; index++) {
            values[index] = values[index] / scale;
        }
        return values;
    }

    private String trimTrailingSlash(String value) {
        String normalized = StringUtils.hasText(value) ? value.trim() : "https://api.openai.com/v1";
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }
}
