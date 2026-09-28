package com.medora.knowledge.retriever;

import com.medora.common.config.EvolutionProperties;
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
    private final EvolutionProperties evolutionProperties;

    public HybridRetriever(KeywordRetriever keywordRetriever, VectorRetriever vectorRetriever,
                           RerankService rerankService, RagProperties ragProperties,
                           EvolutionProperties evolutionProperties) {
        this.keywordRetriever = keywordRetriever;
        this.vectorRetriever = vectorRetriever;
        this.rerankService = rerankService;
        this.ragProperties = ragProperties;
        this.evolutionProperties = evolutionProperties;
    }

    public List<KnowledgeChunkSearchRow> retrieve(String query, String bizScope, int topK) {
        return diagnose(query, bizScope, topK).stream().map(DiagnosticCandidate::row).toList();
    }

    /** Captures genuine retrieval-stage ranks for the ADMIN diagnostics console. */
    public List<DiagnosticCandidate> diagnose(String query, String bizScope, int topK) {
        if (!evolutionProperties.layeredRag()) {
            return diagnoseLegacy(query, bizScope, topK);
        }
        int keywordK = Math.max(topK, ragProperties.getKeywordCandidateK());
        int vectorK = Math.max(topK, ragProperties.getVectorCandidateK());
        List<KnowledgeChunkSearchRow> keyword = keywordRetriever.search(query, bizScope, keywordK);
        List<KnowledgeChunkSearchRow> vector = vectorRetriever.search(query, bizScope, vectorK);
        Map<Long, KnowledgeChunkSearchRow> rows = new LinkedHashMap<>();
        keyword.forEach(row -> rows.putIfAbsent(row.getChunkId(), row)); vector.forEach(row -> rows.putIfAbsent(row.getChunkId(), row));
        List<RankedCandidate> fused = new ReciprocalRankFusion().fuse(toCandidates(keyword), toCandidates(vector), 60);
        Map<Long, RankedCandidate> fusedById = fused.stream().collect(java.util.stream.Collectors.toMap(RankedCandidate::chunkId, item -> item));
        List<KnowledgeChunkSearchRow> candidates = fused.stream()
                .map(candidate -> new ScoredRow(rows.get(candidate.chunkId()), candidate.rrfScore()).toSearchRow())
                .filter(row -> row.getScore() > 0)
                .sorted((left, right) -> Double.compare(right.getScore(), left.getScore()))
                .limit(ragProperties.getRerankCandidateK())
                .toList();
        List<KnowledgeChunkSearchRow> reranked = rerankService.rerank(query, candidates).stream().limit(topK).toList();
        return java.util.stream.IntStream.range(0, reranked.size())
                .mapToObj(index -> { KnowledgeChunkSearchRow row = reranked.get(index); RankedCandidate rank = fusedById.get(row.getChunkId());
                    return new DiagnosticCandidate(row, rank.keywordRank(), rank.vectorRank(), rank.rrfScore(), index + 1); })
                .toList();
    }

    private List<DiagnosticCandidate> diagnoseLegacy(String query, String bizScope, int topK) {
        int candidatePoolSize = Math.max(topK, 8);
        List<KnowledgeChunkSearchRow> keyword = keywordRetriever.search(query, bizScope, candidatePoolSize);
        List<KnowledgeChunkSearchRow> vector = vectorRetriever.search(query, bizScope, candidatePoolSize);
        Map<Long, ScoredRow> merged = new LinkedHashMap<>();
        merge(merged, keyword, ragProperties.getKeywordWeight());
        merge(merged, vector, ragProperties.getVectorWeight());
        List<KnowledgeChunkSearchRow> selected = merged.values().stream()
                .map(ScoredRow::toSearchRow)
                .filter(row -> row.getScore() >= ragProperties.getMinScoreThreshold())
                .sorted((left, right) -> Double.compare(right.getScore(), left.getScore()))
                .limit(topK)
                .toList();
        List<KnowledgeChunkSearchRow> reranked = rerankService.rerank(query, selected);
        Map<Long, Integer> keywordRanks = ranks(keyword);
        Map<Long, Integer> vectorRanks = ranks(vector);
        return java.util.stream.IntStream.range(0, reranked.size())
                .mapToObj(index -> {
                    KnowledgeChunkSearchRow row = reranked.get(index);
                    return new DiagnosticCandidate(row,
                            keywordRanks.getOrDefault(row.getChunkId(), 0),
                            vectorRanks.getOrDefault(row.getChunkId(), 0),
                            row.getScore() == null ? 0 : row.getScore(), index + 1);
                })
                .toList();
    }

    private Map<Long, Integer> ranks(List<KnowledgeChunkSearchRow> rows) {
        Map<Long, Integer> ranks = new LinkedHashMap<>();
        for (int index = 0; index < rows.size(); index++) {
            ranks.putIfAbsent(rows.get(index).getChunkId(), index + 1);
        }
        return ranks;
    }

    private List<RankedCandidate> toCandidates(List<KnowledgeChunkSearchRow> rows) {
        java.util.ArrayList<RankedCandidate> candidates = new java.util.ArrayList<>();
        for (int i = 0; i < rows.size(); i++) candidates.add(new RankedCandidate(rows.get(i).getChunkId(), i + 1, 0, 0));
        return candidates;
    }

    public record DiagnosticCandidate(KnowledgeChunkSearchRow row, int keywordRank, int vectorRank,
                                      double rrfScore, int rerankRank) {
        public Long chunkId() { return row.getChunkId(); }
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
