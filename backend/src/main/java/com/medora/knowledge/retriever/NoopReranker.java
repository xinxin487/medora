package com.medora.knowledge.retriever;

import com.medora.knowledge.repository.KnowledgeChunkSearchRow;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class NoopReranker implements RerankService {

    @Override
    public List<KnowledgeChunkSearchRow> rerank(String query, List<KnowledgeChunkSearchRow> candidates) {
        return candidates;
    }
}
