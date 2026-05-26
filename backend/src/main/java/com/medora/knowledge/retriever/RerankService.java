package com.medora.knowledge.retriever;

import com.medora.knowledge.repository.KnowledgeChunkSearchRow;

import java.util.List;

public interface RerankService {

    List<KnowledgeChunkSearchRow> rerank(String query, List<KnowledgeChunkSearchRow> candidates);
}
