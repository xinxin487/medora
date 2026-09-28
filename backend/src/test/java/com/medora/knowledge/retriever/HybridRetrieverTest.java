package com.medora.knowledge.retriever;

import com.medora.common.config.EvolutionProperties;
import com.medora.knowledge.config.RagProperties;
import com.medora.knowledge.repository.KnowledgeChunkSearchResult;
import com.medora.knowledge.repository.KnowledgeChunkSearchRow;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class HybridRetrieverTest {

    @Test void layeredFlagUsesRrfAndCapturesBothBranchRanks() {
        RagProperties properties = properties();
        HybridRetriever retriever = retriever(true, properties);

        var items = retriever.diagnose("q", "FAQ", 5);

        assertThat(items).extracting(HybridRetriever.DiagnosticCandidate::chunkId).containsExactly(2L, 1L);
        assertThat(items.getFirst().keywordRank()).isEqualTo(2);
        assertThat(items.getFirst().vectorRank()).isEqualTo(1);
        assertThat(items.getFirst().rrfScore()).isGreaterThan(items.get(1).rrfScore());
    }

    @Test void disabledLayeredFlagKeepsLegacyWeightedFusionOrder() {
        RagProperties properties = properties();
        properties.setKeywordWeight(.9);
        properties.setVectorWeight(.1);

        var items = retriever(false, properties).diagnose("q", "FAQ", 5);

        assertThat(items).extracting(HybridRetriever.DiagnosticCandidate::chunkId).containsExactly(1L, 2L);
    }

    private HybridRetriever retriever(boolean layered, RagProperties properties) {
        KeywordRetriever keyword = new KeywordRetriever(null) {
            @Override public List<KnowledgeChunkSearchRow> search(String query, String scope, int topK) {
                return List.of(row(1L, 1), row(2L, .1));
            }
        };
        VectorRetriever vector = new VectorRetriever(null, null) {
            @Override public List<KnowledgeChunkSearchRow> search(String query, String scope, int topK) {
                return List.of(row(2L, 1));
            }
        };
        RerankService identity = (query, candidates) -> candidates;
        EvolutionProperties flags = new EvolutionProperties(false, layered, false, false, false);
        return new HybridRetriever(keyword, vector, identity, properties, flags);
    }

    private RagProperties properties() {
        RagProperties properties = new RagProperties();
        properties.setMinScoreThreshold(0);
        return properties;
    }

    private KnowledgeChunkSearchResult row(Long id, double score) {
        return new KnowledgeChunkSearchResult(id, 0, "section", "content", score,
                UUID.randomUUID(), "title", "FAQ");
    }
}
