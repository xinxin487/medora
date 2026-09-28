package com.medora.agent.context;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

public final class ContextFingerprint {
    private ContextFingerprint() { }

    static String of(String content) {
        String normalized = content.strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(normalized.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    public static String periodPreferenceKey(String content) {
        if (content == null || !(content.contains("偏好") || content.contains("喜欢")
                || content.contains("希望") || content.contains("倾向"))) return "";
        boolean morning = content.contains("上午") || content.contains("早上") || content.contains("午前");
        boolean afternoon = content.contains("下午") || content.contains("晚上") || content.contains("午后");
        if (morning == afternoon) return "";
        return morning ? "PREFERRED_PERIOD:AM" : "PREFERRED_PERIOD:PM";
    }
}
