package com.medora.agent.application;

import com.medora.ai.embedding.EmbeddingService;
import com.medora.ai.embedding.EmbeddingVector;
import com.medora.knowledge.service.RetrievalResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 引用覆盖检测 — Embedding 语义相似度 + 字符串匹配双层。
 * 优先使用 Embedding 余弦相似度判断回复是否引用了检索结果，
 * Embedding 不可用时回退到字符串匹配。
 */
@Component
public class CitationCoverageChecker {

    private static final Logger log = LoggerFactory.getLogger(CitationCoverageChecker.class);

    private static final Pattern NUMBERED_CITATION = Pattern.compile("\\[(\\d+)\\]");
    private static final double SEMANTIC_SIMILARITY_THRESHOLD = 0.75;

    private final EmbeddingService embeddingService;

    public CitationCoverageChecker(EmbeddingService embeddingService) {
        this.embeddingService = embeddingService;
    }

    public CoverageResult check(String reply, List<RetrievalResult> retrievalResults) {
        if (reply == null || reply.isBlank() || retrievalResults == null || retrievalResults.isEmpty()) {
            return new CoverageResult(true, List.of());
        }

        List<String> findings = new ArrayList<>();

        // 检查回复是否使用了编号引用标记 [1] [2]
        Matcher citationMatcher = NUMBERED_CITATION.matcher(reply);
        boolean hasNumberedCitations = citationMatcher.find();

        if (!hasNumberedCitations) {
            // 尝试 Embedding 语义相似度检测
            boolean semanticCovered = checkSemanticCoverage(reply, retrievalResults, findings);
            if (!semanticCovered) {
                // 回退到字符串匹配
                checkStringCoverage(reply, retrievalResults, findings);
            }
        }

        // 检查回复是否包含未在检索结果中出现的重要声明
        checkUncoveredClaims(reply, retrievalResults, findings);

        return new CoverageResult(findings.isEmpty(), findings);
    }

    /**
     * Embedding 语义相似度检测 — 判断回复是否语义上引用了检索结果。
     */
    private boolean checkSemanticCoverage(String reply, List<RetrievalResult> retrievalResults, List<String> findings) {
        try {
            EmbeddingVector replyEmbedding = embeddingService.embed(reply);
            int matched = 0;
            for (RetrievalResult result : retrievalResults) {
                EmbeddingVector snippetEmbedding = embeddingService.embed(result.snippet());
                double similarity = cosineSimilarity(replyEmbedding.values(), snippetEmbedding.values());
                if (similarity >= SEMANTIC_SIMILARITY_THRESHOLD) {
                    matched++;
                }
            }
            double coverageRatio = (double) matched / retrievalResults.size();
            if (coverageRatio < 0.3) {
                findings.add(String.format("语义覆盖不足：回复与检索结果的语义相似度较低（覆盖率 %.0f%%）", coverageRatio * 100));
                log.warn("CitationCoverageChecker: low semantic coverage. matched={}/{}",
                        matched, retrievalResults.size());
                return false;
            }
            return true;
        } catch (Exception ex) {
            // Embedding 不可用，回退到字符串匹配
            log.debug("Semantic coverage check failed, falling back to string matching: {}", ex.getMessage());
            return false;
        }
    }

    /**
     * 字符串匹配降级检测。
     */
    private void checkStringCoverage(String reply, List<RetrievalResult> retrievalResults, List<String> findings) {
        int matchedEntityCount = 0;
        int totalEntityChecks = 0;

        for (RetrievalResult result : retrievalResults) {
            String combined = (result.title() + " " + result.snippet()).toLowerCase();
            List<String> keyEntities = extractKeyEntities(combined);
            totalEntityChecks += keyEntities.size();

            for (String entity : keyEntities) {
                if (reply.toLowerCase().contains(entity)) {
                    matchedEntityCount++;
                }
            }
        }

        if (totalEntityChecks > 0 && (double) matchedEntityCount / totalEntityChecks < 0.3) {
            findings.add("引用覆盖率低：回复未充分引用检索结果中的关键信息");
            log.warn("CitationCoverageChecker: low citation coverage. matched={}/{}",
                    matchedEntityCount, totalEntityChecks);
        }
    }

    private void checkUncoveredClaims(String reply, List<RetrievalResult> retrievalResults, List<String> findings) {
        String[] factualIndicators = {
                "预约规则", "取消规则", "退号", "挂号费", "就诊流程", "报销", "医保",
                "门诊时间", "挂号限制", "取消时限", "爽约", "号源释放", "转诊"
        };
        for (String indicator : factualIndicators) {
            if (reply.contains(indicator)) {
                boolean foundInRetrieval = false;
                for (RetrievalResult result : retrievalResults) {
                    String combined = (result.title() + " " + result.snippet()).toLowerCase();
                    if (combined.contains(indicator)) {
                        foundInRetrieval = true;
                        break;
                    }
                }
                if (!foundInRetrieval) {
                    findings.add("回复包含知识库未覆盖的声明：" + indicator);
                    log.warn("CitationCoverageChecker: uncovered claim '{}' in reply without retrieval support", indicator);
                }
            }
        }
    }

    private List<String> extractKeyEntities(String text) {
        List<String> entities = new ArrayList<>();
        if (!StringUtils.hasText(text)) {
            return entities;
        }
        Pattern entityPattern = Pattern.compile("[\\u4e00-\\u9fff\\d]{3,10}");
        Matcher matcher = entityPattern.matcher(text);
        while (matcher.find()) {
            String entity = matcher.group().trim();
            if (entity.length() >= 3) {
                entities.add(entity);
            }
        }
        return entities.stream().distinct().limit(20).toList();
    }

    private double cosineSimilarity(double[] a, double[] b) {
        if (a.length != b.length || a.length == 0) {
            return 0.0;
        }
        double dot = 0.0, normA = 0.0, normB = 0.0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        double denominator = Math.sqrt(normA) * Math.sqrt(normB);
        return denominator == 0.0 ? 0.0 : dot / denominator;
    }

    public record CoverageResult(
            boolean covered,
            List<String> findings
    ) {
    }
}
