package com.medora.agent.application;

import com.medora.knowledge.service.RetrievalResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class CitationCoverageChecker {

    private static final Logger log = LoggerFactory.getLogger(CitationCoverageChecker.class);

    private static final Pattern NUMBERED_CITATION = Pattern.compile("\\[(\\d+)\\]");

    public CoverageResult check(String reply, List<RetrievalResult> retrievalResults) {
        if (reply == null || reply.isBlank() || retrievalResults == null || retrievalResults.isEmpty()) {
            return new CoverageResult(true, List.of());
        }

        List<String> findings = new ArrayList<>();

        // 检查回复是否使用了编号引用标记 [1] [2]
        Matcher citationMatcher = NUMBERED_CITATION.matcher(reply);
        boolean hasNumberedCitations = citationMatcher.find();

        if (!hasNumberedCitations && !retrievalResults.isEmpty()) {
            // 没有显式引用标记，检查是否用到了知识片段中的关键实体
            int matchedEntityCount = 0;
            int totalEntityChecks = 0;

            for (RetrievalResult result : retrievalResults) {
                String snippet = result.snippet();
                String title = result.title();
                String combined = (title + " " + snippet).toLowerCase();

                // 从检索片段提取关键中文实体（4字以上连续中文/数字组合）
                List<String> keyEntities = extractKeyEntities(combined);
                totalEntityChecks += keyEntities.size();

                for (String entity : keyEntities) {
                    if (reply.toLowerCase().contains(entity)) {
                        matchedEntityCount++;
                    }
                }
            }

            // 如果大部分关键实体未被引用，标记为低覆盖
            if (totalEntityChecks > 0 && (double) matchedEntityCount / totalEntityChecks < 0.3) {
                findings.add("引用覆盖率低：回复未充分引用检索结果中的关键信息");
                log.warn("CitationCoverageChecker: low citation coverage. matched={}/{}",
                        matchedEntityCount, totalEntityChecks);
            }
        }

        // 检查回复是否包含未在检索结果中出现的重要声明
        String[] factualIndicators = {"预约规则", "取消规则", "退号", "挂号费", "就诊流程", "报销", "医保"};
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

        return new CoverageResult(findings.isEmpty(), findings);
    }

    private List<String> extractKeyEntities(String text) {
        List<String> entities = new ArrayList<>();
        if (!StringUtils.hasText(text)) {
            return entities;
        }
        // 提取3-10字的中文/数字组合作为关键实体
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

    public record CoverageResult(
            boolean covered,
            List<String> findings
    ) {
    }
}
