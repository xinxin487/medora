package com.medora.knowledge.retriever;

import com.medora.ai.embedding.EmbeddingService;
import com.medora.ai.embedding.EmbeddingVector;
import com.medora.knowledge.repository.KnowledgeChunkSearchRow;
import com.medora.knowledge.repository.KnowledgeVectorSearchRepository;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

@Component
public class VectorRetriever {

    private final EmbeddingService embeddingService;
    private final KnowledgeVectorSearchRepository knowledgeVectorSearchRepository;

    public VectorRetriever(EmbeddingService embeddingService,
                           KnowledgeVectorSearchRepository knowledgeVectorSearchRepository) {
        this.embeddingService = embeddingService;
        this.knowledgeVectorSearchRepository = knowledgeVectorSearchRepository;
    }

    public List<KnowledgeChunkSearchRow> search(String query, String bizScope, int topK) {
        if (!StringUtils.hasText(query)) {
            return List.of();
        }
        EmbeddingVector embedding = embeddingService.embed(query.trim());
        return knowledgeVectorSearchRepository.searchPublished(embedding.values(), bizScope, Math.max(1, topK));
    }
}
