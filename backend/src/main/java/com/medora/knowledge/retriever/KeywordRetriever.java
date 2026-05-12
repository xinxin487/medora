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
        return knowledgeChunkRepository.searchPublished(query.trim(), normalizeToNull(bizScope), Math.max(1, topK));
    }

    private String normalizeToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
