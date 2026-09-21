package com.medora.memory.governance;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public final class MemoryFingerprint {
    private MemoryFingerprint() { }
    public static String sha256(long userId, MemoryCandidate candidate) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest((userId + "|" + candidate.memoryType() + "|" + candidate.scopeKey() + "|" + candidate.normalizedValue()).getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (java.security.NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }
}
