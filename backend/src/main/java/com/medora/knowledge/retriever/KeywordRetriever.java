package com.medora.knowledge.retriever;

import com.medora.knowledge.repository.KnowledgeChunkRepository;
import com.medora.knowledge.repository.KnowledgeChunkSearchRow;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

@Component
public class KeywordRetriever {

    private final KnowledgeChunkRepository knowledgeChunkRepository;

    public KeywordRetriever(KnowledgeChunkRepository knowledgeChunkRepository) {
        this.knowledgeChunkRepository = knowledgeChunkRepository;
    }

    public List<KnowledgeChunkSearchRow> search(String query, String bizScope, int topK) {
        if (!StringUtils.hasText(query)) {
            return List.of();
        }
        String trimmed = query.trim();
        String ftsQuery = segmentChinese(trimmed);
        return knowledgeChunkRepository.searchPublished(trimmed, ftsQuery, normalizeToNull(bizScope), Math.max(1, topK));
    }

    static String segmentChinese(String query) {
        StringBuilder sb = new StringBuilder();
        for (char c : query.toCharArray()) {
            if (Character.UnicodeScript.of(c) == Character.UnicodeScript.HAN) {
                sb.append(c).append(' ');
            } else {
                sb.append(c);
            }
        }
        return sb.toString().trim();
    }

    private String normalizeToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
