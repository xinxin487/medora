package com.medora.knowledge.retriever;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medora.knowledge.repository.KnowledgeChunkSearchResult;
import com.medora.knowledge.repository.KnowledgeChunkSearchRow;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class LlmRerankerTest {

    private final KnowledgeChunkSearchRow first = row(1L, "first");
    private final KnowledgeChunkSearchRow second = row(2L, "second");
    private final List<KnowledgeChunkSearchRow> original = List.of(first, second);

    @Test void validResponseReordersEveryCandidate() {
        LlmReranker reranker = reranker("""
                [{"chunkId":2,"relevance":0.9,"reason":"direct"},
                 {"chunkId":1,"relevance":0.2,"reason":"weak"}]
                """);

        assertThat(reranker.rerank("q", original)).containsExactly(second, first);
    }

    @Test void invalidResponsesFallBackToOriginalOrder() {
        assertFallback("not-json");
        assertFallback("[{\"chunkId\":999,\"relevance\":1,\"reason\":\"unknown\"}]");
        assertFallback("[{\"chunkId\":1,\"relevance\":1,\"reason\":\"a\"},"
                + "{\"chunkId\":1,\"relevance\":0,\"reason\":\"duplicate\"}]");
        assertFallback("[{\"chunkId\":1,\"relevance\":1,\"reason\":\"omitted second\"}]");
        assertFallback("[{\"chunkId\":2,\"relevance\":1},{\"chunkId\":1,\"relevance\":0}]");
        assertFallback("[{\"chunkId\":2,\"reason\":\"missing score\"},"
                + "{\"chunkId\":1,\"relevance\":0,\"reason\":\"weak\"}]");
    }

    @Test void clientFailureFallsBackToOriginalOrder() {
        LlmReranker reranker = new LlmReranker(new ObjectMapper(), (query, candidates) -> {
            throw new IllegalStateException("timeout");
        });

        assertThat(reranker.rerank("q", original)).containsExactlyElementsOf(original);
    }

    @Test void clampsScoresBeforeOrdering() {
        LlmReranker reranker = reranker("""
                [{"chunkId":1,"relevance":2,"reason":"high"},
                 {"chunkId":2,"relevance":3,"reason":"also high"}]
                """);

        assertThat(reranker.rerank("q", original)).containsExactly(first, second);
    }

    private void assertFallback(String response) {
        assertThat(reranker(response).rerank("q", original)).containsExactlyElementsOf(original);
    }

    private LlmReranker reranker(String response) {
        return new LlmReranker(new ObjectMapper(), (query, candidates) -> response);
    }

    private KnowledgeChunkSearchResult row(long id, String content) {
        return new KnowledgeChunkSearchResult(id, 0, "s", content, .5,
                UUID.randomUUID(), "t", "GENERAL");
    }
}
