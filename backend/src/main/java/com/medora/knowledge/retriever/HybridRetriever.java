package com.medora.knowledge.retriever;

import com.medora.knowledge.config.RagProperties;
import com.medora.knowledge.repository.KnowledgeChunkSearchRow;
import com.medora.knowledge.repository.KnowledgeChunkSearchResult;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class HybridRetriever {

    private final KeywordRetriever keywordRetriever;
    private final VectorRetriever vectorRetriever;
    private final RerankService rerankService;
    private final RagProperties ragProperties;

    public HybridRetriever(KeywordRetriever keywordRetriever, VectorRetriever vectorRetriever,
                           RerankService rerankService, RagProperties ragProperties) {
        this.keywordRetriever = keywordRetriever;
        this.vectorRetriever = vectorRetriever;
        this.rerankService = rerankService;
        this.ragProperties = ragProperties;
    }

    public List<KnowledgeChunkSearchRow> retrieve(String query, String bizScope, int topK) {
        int candidatePoolSize = Math.max(topK, 8);
        double kwWeight = ragProperties.getKeywordWeight();
        double vecWeight = ragProperties.getVectorWeight();
        double minThreshold = ragProperties.getMinScoreThreshold();
        Map<Long, ScoredRow> merged = new LinkedHashMap<>();
        merge(merged, keywordRetriever.search(query, bizScope, candidatePoolSize), kwWeight);
        merge(merged, vectorRetriever.search(query, bizScope, candidatePoolSize), vecWeight);
        return rerankService.rerank(query, merged.values().stream()
                .map(ScoredRow::toSearchRow)
                .filter(row -> row.getScore() >= minThreshold)
                .sorted((left, right) -> Double.compare(right.getScore(), left.getScore()))
                .limit(topK)
                .toList());
    }

    private void merge(Map<Long, ScoredRow> merged, List<KnowledgeChunkSearchRow> rows, double weight) {
        double maxScore = rows.stream()
                .map(KnowledgeChunkSearchRow::getScore)
                .filter(score -> score != null && score > 0)
                .mapToDouble(Double::doubleValue)
                .max()
                .orElse(1.0);
        for (KnowledgeChunkSearchRow row : rows) {
            double normalizedScore = (row.getScore() == null ? 0.0 : row.getScore()) / maxScore;
            merged.compute(row.getChunkId(), (chunkId, existing) -> {
                if (existing == null) {
                    return new ScoredRow(row, normalizedScore * weight);
                }
                existing.addScore(normalizedScore * weight);
                return existing;
            });
        }
    }

    private static class ScoredRow {
        private final KnowledgeChunkSearchRow row;
        private double score;

        private ScoredRow(KnowledgeChunkSearchRow row, double score) {
            this.row = row;
            this.score = score;
        }

        private void addScore(double value) {
            this.score += value;
        }

        private KnowledgeChunkSearchRow toSearchRow() {
            return new KnowledgeChunkSearchResult(
                    row.getChunkId(),
                    row.getChunkIndex(),
                    row.getSectionPath(),
                    row.getContent(),
                    score,
                    row.getDocumentPublicId(),
                    row.getTitle(),
                    row.getBizScope()
            );
        }
    }
}
