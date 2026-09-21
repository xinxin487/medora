package com.medora.toolgateway.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.springframework.stereotype.Component;

/** Builds an audit-safe argument summary and a correlation-only digest. */
@Component
public class ToolAuditSanitizer {

    private static final Set<String> SENSITIVE_KEYS = Set.of(
            "apikey", "authorization", "email", "medicalrecord", "password", "phone",
            "secret", "symptom", "symptoms", "token"
    );

    private final ObjectMapper objectMapper = new ObjectMapper();

    public SanitizedArguments sanitize(Map<String, Object> arguments) {
        Map<String, Object> original = arguments == null ? Map.of() : arguments;
        return new SanitizedArguments(redactMap(original), sha256(canonicalJson(original)));
    }

    private Map<String, Object> redactMap(Map<String, Object> values) {
        Map<String, Object> result = new LinkedHashMap<>();
        values.forEach((key, value) -> result.put(key, isSensitive(key) ? "***" : redact(value)));
        return Map.copyOf(result);
    }

    private Object redact(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> nested = new LinkedHashMap<>();
            map.forEach((key, nestedValue) -> nested.put(String.valueOf(key), isSensitive(String.valueOf(key)) ? "***" : redact(nestedValue)));
            return Map.copyOf(nested);
        }
        if (value instanceof Collection<?> collection) {
            return collection.stream().map(this::redact).toList();
        }
        if (value != null && value.getClass().isArray()) {
            int length = java.lang.reflect.Array.getLength(value);
            var items = new ArrayList<>(length);
            for (int index = 0; index < length; index++) {
                items.add(redact(java.lang.reflect.Array.get(value, index)));
            }
            return List.copyOf(items);
        }
        return value;
    }

    private boolean isSensitive(String key) {
        String normalized = key.replaceAll("[^A-Za-z]", "").toLowerCase(Locale.ROOT);
        return SENSITIVE_KEYS.stream().anyMatch(normalized::contains);
    }

    private String canonicalJson(Map<String, Object> arguments) {
        try {
            return objectMapper.writeValueAsString(canonicalize(arguments));
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("tool arguments cannot be audited", ex);
        }
    }

    private Object canonicalize(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> sorted = new TreeMap<>();
            map.forEach((key, nestedValue) -> sorted.put(String.valueOf(key), canonicalize(nestedValue)));
            return sorted;
        }
        if (value instanceof Collection<?> collection) {
            return collection.stream().map(this::canonicalize).toList();
        }
        return value;
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    public record SanitizedArguments(Map<String, Object> summary, String argumentsHash) {
    }
}
