package com.medora.memory.governance;

import java.time.OffsetDateTime;

public record MemoryCandidate(String memoryType, String scopeKey, String rawValue, String normalizedValue,
                              String summary, double confidence, String source, String sourceReference,
                              OffsetDateTime occurredAt, OffsetDateTime expiresAt) {
    public static MemoryCandidate of(String type, String value) { return of(type, value, "USER_EXPLICIT", .8); }
    public static MemoryCandidate of(String type, String value, String source, double confidence) {
        return new MemoryCandidate(type, "GLOBAL", value, null, value, confidence, source, null, OffsetDateTime.now(), null);
    }
    public MemoryCandidate withNormalizedValue(String value) {
        return new MemoryCandidate(memoryType, scopeKey, rawValue, value, summary, confidence, source, sourceReference, occurredAt, expiresAt);
    }
}
