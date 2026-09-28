package com.medora.knowledge.retriever;

import com.medora.knowledge.repository.KnowledgeChunkSearchRow;
import java.util.List;

public class NoopReranker implements RerankService {

    @Override
    public List<KnowledgeChunkSearchRow> rerank(String query, List<KnowledgeChunkSearchRow> candidates) {
        return candidates;
    }
}
