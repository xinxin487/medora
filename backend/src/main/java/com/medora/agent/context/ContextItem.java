package com.medora.agent.context;

import java.time.OffsetDateTime;

/** Sanitized context candidate; content is never persisted in diagnostics. */
public record ContextItem(String source, String type, String fingerprint, String content, double confidence,
                          String sourceQuality, OffsetDateTime expiresAt, int estimatedTokens,
                          OffsetDateTime occurredAt) {
    public ContextItem(String source, String type, String fingerprint, String content, double confidence,
                       String sourceQuality, OffsetDateTime expiresAt, int estimatedTokens) {
        this(source, type, fingerprint, content, confidence, sourceQuality, expiresAt, estimatedTokens, null);
    }
}
