package com.medora.knowledge.chunk;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class DocumentChunker {

    private static final int TARGET_SIZE = 700;
    private static final int OVERLAP_SIZE = 120;

    public List<DocumentChunk> chunk(String content) {
        String cleaned = clean(content);
        List<DocumentChunk> chunks = new ArrayList<>();
        String sectionPath = "全文";
        StringBuilder buffer = new StringBuilder();

        for (String paragraph : cleaned.split("\\R{2,}")) {
            String trimmed = paragraph.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (looksLikeHeading(trimmed)) {
                sectionPath = trimmed.replaceFirst("^#+\\s*", "");
            }
            if (buffer.length() + trimmed.length() > TARGET_SIZE && !buffer.isEmpty()) {
                addChunk(chunks, sectionPath, buffer.toString());
                buffer = new StringBuilder(tail(buffer.toString()));
            }
            if (!buffer.isEmpty()) {
                buffer.append("\n\n");
            }
            buffer.append(trimmed);
        }

        if (!buffer.isEmpty()) {
            addChunk(chunks, sectionPath, buffer.toString());
        }
        return chunks;
    }

    private void addChunk(List<DocumentChunk> chunks, String sectionPath, String content) {
        chunks.add(new DocumentChunk(chunks.size(), sectionPath, content.trim()));
    }

    private String clean(String content) {
        return content == null ? "" : content
                .replace("\u0000", "")
                .replaceAll("[ \\t]+", " ")
                .replaceAll("\\R{3,}", "\n\n")
                .trim();
    }

    private boolean looksLikeHeading(String paragraph) {
        return paragraph.startsWith("#")
                || (paragraph.length() <= 40 && paragraph.matches(".*(规则|须知|流程|科室|医生|FAQ|问答).*"));
    }

    private String tail(String value) {
        if (value.length() <= OVERLAP_SIZE) {
            return value;
        }
        return value.substring(value.length() - OVERLAP_SIZE);
    }
}
