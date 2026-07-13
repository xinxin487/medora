package com.medora.eval.llm;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Phase 10 W4 — 计算 (systemPrompt + userPrompt) 的稳定 hash key。
 *
 * <p>用 SHA-256 而非 xxhash 等更快算法:JDK 自带,零依赖,且碰撞概率可以忽略。
 * 截断为前 16 个 hex 字符(64 bit)足以避免任何现实碰撞。
 */
public final class PromptHasher {

    private PromptHasher() {}

    public static String hash(String systemPrompt, String userPrompt) {
        String sys = systemPrompt == null ? "" : systemPrompt;
        String usr = userPrompt == null ? "" : userPrompt;
        String combined = "S:" + sys + "\nU:" + usr;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(combined.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (int i = 0; i < 8 && i < digest.length; i++) {
                int b = digest[i] & 0xFF;
                sb.append(Character.forDigit(b >> 4, 16));
                sb.append(Character.forDigit(b & 0x0F, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }
}
