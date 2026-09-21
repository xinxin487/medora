package com.medora.knowledge.service;

import com.medora.knowledge.api.response.KnowledgeSearchItem;
import com.medora.knowledge.api.response.KnowledgeSearchResponse;
import com.medora.knowledge.config.RagProperties;
import com.medora.knowledge.repository.KnowledgeChunkSearchRow;
import com.medora.knowledge.repository.KnowledgeChunkRepository;
import com.medora.knowledge.retriever.HybridRetriever;
import com.medora.knowledge.retriever.RetrievalResultAssembler;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

@Service
public class RetrievalService {

    private final HybridRetriever hybridRetriever;
    private final RetrievalResultAssembler retrievalResultAssembler;
    private final RagProperties ragProperties;
    private final KnowledgeChunkRepository chunks;

    public RetrievalService(HybridRetriever hybridRetriever,
                            RetrievalResultAssembler retrievalResultAssembler,
                            RagProperties ragProperties) {
        this(hybridRetriever, retrievalResultAssembler, ragProperties, null);
    }

    @Autowired
    public RetrievalService(HybridRetriever hybridRetriever,
                            RetrievalResultAssembler retrievalResultAssembler,
                            RagProperties ragProperties,
                            KnowledgeChunkRepository chunks) {
        this.hybridRetriever = hybridRetriever;
        this.retrievalResultAssembler = retrievalResultAssembler;
        this.ragProperties = ragProperties;
        this.chunks = chunks;
    }

    public KnowledgeSearchResponse searchForAdmin(String query, String bizScope, Integer topK) {
        List<RetrievalResult> results = retrieve(query, bizScope, topK == null ? ragProperties.getDefaultTopK() : topK);
        return new KnowledgeSearchResponse(
                query.trim(),
                results.stream()
                        .map(item -> new KnowledgeSearchItem(
                                item.documentId(),
                                item.chunkId(),
                                item.chunkIndex(),
                                item.title(),
                                item.bizScope(),
                                item.score(),
                                item.sectionPath(),
                                item.snippet()
                        ))
                        .toList()
        );
    }

    public List<RetrievalResult> retrieve(String query, String bizScope, int topK) {
        if (!StringUtils.hasText(query)) {
            return List.of();
        }
        String normalizedQuery = query.trim();
        int limit = Math.min(Math.max(topK, 1), ragProperties.getMaxTopK());
        QueryFeatures queryFeatures = QueryFeatures.from(normalizedQuery);
        return hybridRetriever.retrieve(normalizedQuery, bizScope, limit).stream()
                .filter(row -> isRelevant(row, queryFeatures))
                .map(row -> retrievalResultAssembler.assemble(row, parentContent(row.getChunkId()), 800))
                .toList();
    }

    private String parentContent(Long childId) {
        return chunks == null ? null : chunks.findParentContentByChildId(childId).orElse(null);
    }

    private boolean isRelevant(KnowledgeChunkSearchRow row, QueryFeatures queryFeatures) {
        if (queryFeatures.isBlank()) {
            return false;
        }
        String haystack = normalizeForMatch(String.join(" ",
                nullToEmpty(row.getTitle()),
                nullToEmpty(row.getSectionPath()),
                nullToEmpty(row.getContent())));
        if (haystack.isBlank()) {
            return false;
        }
        if (haystack.contains(queryFeatures.normalized())) {
            return true;
        }
        if (queryFeatures.strongTerms().stream().anyMatch(haystack::contains)) {
            return true;
        }
        int weakMatches = 0;
        for (String weakTerm : queryFeatures.weakTerms()) {
            if (haystack.contains(weakTerm) && ++weakMatches >= 2) {
                return true;
            }
        }
        return false;
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String normalizeForMatch(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        String normalized = value.toLowerCase();
        for (int index = 0; index < normalized.length(); index++) {
            char current = normalized.charAt(index);
            if (Character.isLetterOrDigit(current) || isCjk(current)) {
                builder.append(current);
            }
        }
        return builder.toString();
    }

    private static boolean isCjk(char value) {
        Character.UnicodeScript script = Character.UnicodeScript.of(value);
        return script == Character.UnicodeScript.HAN
                || script == Character.UnicodeScript.HIRAGANA
                || script == Character.UnicodeScript.KATAKANA
                || script == Character.UnicodeScript.HANGUL;
    }

    private record QueryFeatures(
            String normalized,
            List<String> strongTerms,
            List<String> weakTerms
    ) {

        private static QueryFeatures from(String query) {
            String normalized = normalizeForMatch(query);
            if (normalized.isBlank()) {
                return new QueryFeatures("", List.of(), List.of());
            }

            LinkedHashSet<String> strongTerms = new LinkedHashSet<>();
            LinkedHashSet<String> weakTerms = new LinkedHashSet<>();
            strongTerms.add(normalized);

            if (normalized.length() <= 2) {
                strongTerms.add(normalized);
            } else {
                for (int index = 0; index < normalized.length() - 1; index++) {
                    strongTerms.add(normalized.substring(index, index + 2));
                }
                for (int index = 0; index < normalized.length(); index++) {
                    weakTerms.add(normalized.substring(index, index + 1));
                }
            }

            return new QueryFeatures(
                    normalized,
                    new ArrayList<>(strongTerms),
                    new ArrayList<>(weakTerms)
            );
        }

        private boolean isBlank() {
            return normalized.isBlank();
        }
    }
}
