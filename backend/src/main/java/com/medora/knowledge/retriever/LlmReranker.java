package com.medora.knowledge.retriever;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medora.knowledge.repository.KnowledgeChunkSearchRow;
import java.util.*;

/** Strict parser: malformed, incomplete, duplicate, or unknown results preserve RRF order. */
public class LlmReranker implements RerankService {
    @FunctionalInterface public interface Client { String rerank(String query, List<KnowledgeChunkSearchRow> candidates); }
    private final ObjectMapper mapper; private final Client client;
    public LlmReranker(ObjectMapper mapper, Client client) { this.mapper = mapper; this.client = client; }
    @Override public List<KnowledgeChunkSearchRow> rerank(String query, List<KnowledgeChunkSearchRow> candidates) {
        List<KnowledgeChunkSearchRow> original = candidates == null ? List.of() : List.copyOf(candidates);
        try {
            List<RerankDecision> decisions = mapper.readValue(client.rerank(query, original), new TypeReference<>() {});
            if (decisions == null || decisions.size() != original.size()) return original;
            if (decisions.stream().anyMatch(this::invalid)) return original;
            Set<Long> expected = original.stream().map(KnowledgeChunkSearchRow::getChunkId).collect(java.util.stream.Collectors.toSet());
            Set<Long> actual = decisions.stream().map(RerankDecision::chunkId).collect(java.util.stream.Collectors.toSet());
            if (actual.size() != decisions.size() || !actual.equals(expected)) return original;
            Map<Long, KnowledgeChunkSearchRow> byId = new HashMap<>(); original.forEach(row -> byId.put(row.getChunkId(), row));
            return decisions.stream().sorted(Comparator.comparingDouble(this::clampedRelevance).reversed()).map(d -> byId.get(d.chunkId())).toList();
        } catch (RuntimeException | java.io.IOException ignored) { return original; }
    }

    private boolean invalid(RerankDecision decision) {
        return decision == null || decision.chunkId() == null || decision.relevance() == null
                || !Double.isFinite(decision.relevance())
                || decision.reason() == null || decision.reason().isBlank();
    }

    private double clampedRelevance(RerankDecision decision) {
        return Math.max(0, Math.min(1, decision.relevance()));
    }
}
