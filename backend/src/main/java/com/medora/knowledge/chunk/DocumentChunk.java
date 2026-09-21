package com.medora.knowledge.chunk;

public record DocumentChunk(
        int index,
        String sectionPath,
        String content,
        String chunkType,
        int parentOrdinal,
        int tokenCount,
        String contentHash
) {
    public DocumentChunk(int index, String sectionPath, String content) {
        this(index, sectionPath, content, "CHILD", -1,
                Math.max(1, (int) Math.ceil((content == null ? 0 : content.codePointCount(0, content.length())) / 2.0)), hash(content));
    }
    private static String hash(String content) { try { return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest((content == null ? "" : content).getBytes(java.nio.charset.StandardCharsets.UTF_8))); } catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); } }
}
