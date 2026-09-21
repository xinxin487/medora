package com.medora.knowledge.retriever;

import com.medora.knowledge.config.RagProperties;
import com.medora.knowledge.repository.KnowledgeChunkSearchResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HybridRetrieverTest {
    @Test void capturesKeywordVectorAndRrfRanksForDiagnostics() {
        KeywordRetriever keyword = mock(KeywordRetriever.class);
        VectorRetriever vector = mock(VectorRetriever.class);
        RerankService reranker = (query, candidates) -> candidates;
        when(keyword.search("q", "FAQ", 20)).thenReturn(List.of(row(1L), row(2L)));
        when(vector.search("q", "FAQ", 20)).thenReturn(List.of(row(2L)));

        var items = new HybridRetriever(keyword, vector, reranker, new RagProperties()).diagnose("q", "FAQ", 5);

        assertThat(items).extracting(HybridRetriever.DiagnosticCandidate::chunkId).containsExactly(2L, 1L);
        assertThat(items.getFirst().keywordRank()).isEqualTo(2);
        assertThat(items.getFirst().vectorRank()).isEqualTo(1);
        assertThat(items.getFirst().rrfScore()).isGreaterThan(items.get(1).rrfScore());
    }

    private KnowledgeChunkSearchResult row(Long id) {
        return new KnowledgeChunkSearchResult(id, 0, "section", "content", .5, UUID.randomUUID(), "title", "FAQ");
    }
}
