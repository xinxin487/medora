package com.medora.knowledge.chunk;

public record DocumentChunk(
        int index,
        String sectionPath,
        String content
) {
}
