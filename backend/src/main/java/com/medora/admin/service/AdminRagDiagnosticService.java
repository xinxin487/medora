package com.medora.admin.service;

import com.medora.knowledge.service.RetrievalResult;
import com.medora.knowledge.service.RetrievalService;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** ADMIN diagnostic view: deliberately exposes only the user-visible snippet. */
@Service
public class AdminRagDiagnosticService {
    private final RetrievalService retrievalService;

    public AdminRagDiagnosticService(RetrievalService retrievalService) {
        this.retrievalService = retrievalService;
    }

    public DiagnosticResponse diagnose(String query, String bizScope, Integer topK) {
        long started = System.nanoTime();
        int limit = topK == null ? 5 : Math.min(Math.max(topK, 1), 10);
        List<DiagnosticItem> items = retrievalService.diagnose(query, bizScope, limit).stream()
                .map(this::toItem)
                .toList();
        Map<String, String> filters = new LinkedHashMap<>();
        filters.put("bizScope", bizScope == null || bizScope.isBlank() ? "ALL" : bizScope);
        filters.put("topK", String.valueOf(limit));
        return new DiagnosticResponse(query.trim(), Map.copyOf(filters), items,
                (System.nanoTime() - started) / 1_000_000, items.isEmpty() ? "NO_MATCH" : "NONE");
    }

    private DiagnosticItem toItem(RetrievalService.DiagnosticRetrievalResult diagnostic) {
        RetrievalResult result = diagnostic.result();
        return new DiagnosticItem(result.documentId(), result.chunkId(), null, result.sectionPath(), -1,
                diagnostic.lexicalRank(), diagnostic.vectorRank(), diagnostic.rrfScore(), result.score(), result.snippet(), "NONE");
    }

    public record DiagnosticResponse(String query, Map<String, String> appliedFilters, List<DiagnosticItem> items,
                                     long latencyMs, String fallbackReason) { }
    public record DiagnosticItem(String documentId, Long childChunkId, Long parentChunkId, String sectionPath,
                                 Integer pageNumber, int lexicalRank, int vectorRank, Double rrfScore,
                                 Double rerankScore, String snippet, String fallbackReason) { }
}
