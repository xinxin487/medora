package com.medora.knowledge.chunk;

import java.util.*;

/** Keeps Markdown headings, tables, lists, and adjacent FAQ question/answer records intact. */
public class StructureAwareSplitter {
    public List<DocumentChunk> split(String text) {
        String source = text == null ? "" : text.trim(); if (source.isEmpty()) return List.of();
        List<String> sections = new ArrayList<>(); StringBuilder current = new StringBuilder();
        for (String line : source.split("\\R")) {
            if (line.startsWith("#") && !current.isEmpty()) { sections.add(current.toString().trim()); current = new StringBuilder(); }
            if (!current.isEmpty()) current.append('\n'); current.append(line);
        }
        if (!current.isEmpty()) sections.add(current.toString().trim());
        List<DocumentChunk> out = new ArrayList<>(); int index = 0;
        for (int parent = 0; parent < sections.size(); parent++) {
            String section = sections.get(parent); String path = section.lines().findFirst().filter(line -> line.startsWith("#")).map(line -> line.replaceFirst("^#+\\s*", "")).orElse("全文");
            out.add(new DocumentChunk(index++, path, section, "PARENT", parent, tokens(section), hash(section)));
            for (String child : children(section)) out.add(new DocumentChunk(index++, path, child, "CHILD", parent, tokens(child), hash(child)));
        }
        return List.copyOf(out);
    }
    private List<String> children(String section) {
        List<String> result = new ArrayList<>(); StringBuilder pair = new StringBuilder();
        for (String block : section.split("\\R{2,}")) { String value = block.trim(); if (value.isEmpty() || value.startsWith("#")) continue;
            boolean question = value.startsWith("Q:") || value.startsWith("Q：") || value.startsWith("问:") || value.startsWith("问：");
            if (question && !pair.isEmpty()) { result.add(pair.toString()); pair = new StringBuilder(); }
            if (!pair.isEmpty()) pair.append('\n'); pair.append(value);
            if (!(question || value.startsWith("A:") || value.startsWith("A：") || value.startsWith("答:") || value.startsWith("答："))) { result.add(pair.toString()); pair = new StringBuilder(); }
        }
        if (!pair.isEmpty()) result.add(pair.toString()); return result;
    }
    private int tokens(String value) { return Math.max(1, (int)Math.ceil(value.codePointCount(0,value.length()) / 2.0)); }
    private String hash(String value) { try { return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8))); } catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); } }
}
