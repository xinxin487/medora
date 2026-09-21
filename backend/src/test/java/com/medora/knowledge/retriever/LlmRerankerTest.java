package com.medora.knowledge.retriever;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medora.knowledge.repository.KnowledgeChunkSearchResult;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

class LlmRerankerTest {
    @Test void invalidResponseFallsBackToOriginalOrder() {
        var first = new KnowledgeChunkSearchResult(1L,0,"s","a",.5, UUID.randomUUID(),"t","GENERAL");
        var second = new KnowledgeChunkSearchResult(2L,1,"s","b",.4, UUID.randomUUID(),"t","GENERAL");
        var reranker = new LlmReranker(new ObjectMapper(), (query, candidates) -> "[{\"chunkId\":999,\"relevance\":1}]");
        assertThat(reranker.rerank("q", List.of(first, second))).containsExactly(first, second);
    }
}
