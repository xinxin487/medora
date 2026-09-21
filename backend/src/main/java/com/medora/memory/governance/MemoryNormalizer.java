package com.medora.memory.governance;

import java.util.Locale;

public class MemoryNormalizer {
    public MemoryCandidate normalize(MemoryCandidate candidate) {
        if ("RECENT_SYMPTOM".equals(candidate.memoryType()) && candidate.expiresAt() == null) {
            throw new IllegalArgumentException("RECENT_SYMPTOM requires expiresAt");
        }
        String value = candidate.rawValue() == null ? "" : candidate.rawValue().trim();
        if ("PREFERRED_PERIOD".equals(candidate.memoryType())) {
            value = switch (value) { case "上午", "早上", "午前" -> "AM"; case "下午", "午后" -> "PM"; case "晚上", "夜间" -> "EVENING"; default -> value.toUpperCase(Locale.ROOT); };
        } else {
            value = value.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        }
        return candidate.withNormalizedValue(value);
    }
}
