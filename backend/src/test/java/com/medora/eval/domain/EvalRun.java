package com.medora.eval.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Phase 10 — 单次 EvalRunner 运行的完整结果。
 *
 * <p>由 {@link com.medora.eval.runner.EvalRunner} 在跑完所有 case 后构造,
 * 经 {@link com.medora.eval.report.JsonReportWriter} / {@link com.medora.eval.report.MarkdownReportWriter}
 * 输出。
 */
public record EvalRun(
        UUID runId,
        Instant startedAt,
        Instant finishedAt,
        String commitSha,
        String branch,
        Map<String, String> config,
        List<CaseResult> results,
        AggregateMetrics aggregate
) {
    public EvalRun {
        if (results == null) {
            results = List.of();
        }
        if (aggregate == null) {
            aggregate = AggregateMetrics.empty();
        }
    }

    public Duration duration() {
        if (startedAt == null || finishedAt == null) {
            return Duration.ZERO;
        }
        return Duration.between(startedAt, finishedAt);
    }

    public record CaseResult(
            String caseId,
            TestCase.Category category,
            Status status,
            ActualBehavior actual,
            List<Score> scores,
            String error
    ) {
        public CaseResult {
            if (scores == null) {
                scores = List.of();
            }
        }

        public double weightedScore() {
            if (scores.isEmpty()) {
                return 0.0;
            }
            double totalWeight = scores.stream().mapToDouble(Score::weight).sum();
            if (totalWeight <= 0) {
                return 0.0;
            }
            double weighted = scores.stream()
                    .mapToDouble(s -> s.score() * s.weight())
                    .sum();
            return weighted / totalWeight;
        }

        public enum Status {
            PASSED,
            FAILED,
            ERROR
        }
    }

    public record ActualBehavior(
            String reply,
            List<String> transferChain,
            List<Map<String, Object>> toolEvents,
            String hallucinationRisk,
            long latencyMs,
            String errorMessage
    ) {
        public ActualBehavior {
            if (transferChain == null) {
                transferChain = List.of();
            }
            if (toolEvents == null) {
                toolEvents = List.of();
            }
        }

        public static ActualBehavior empty() {
            return new ActualBehavior("", List.of(), List.of(), "LOW", 0L, null);
        }
    }

    /**
     * 单个 Judge 对一个 case 的评分结果。
     *
     * @param judgeName 评分器名(如 "RouteJudge")
     * @param pass      是否通过本评分器的硬性条件
     * @param score     0.0-1.0 连续分(用于聚合)
     * @param weight    本评分器在加权平均中的权重
     * @param reasoning 评分理由(失败时用于报告)
     */
    public record Score(
            String judgeName,
            boolean pass,
            double score,
            double weight,
            String reasoning
    ) {
        public Score {
            if (weight <= 0) {
                weight = 1.0;
            }
            if (score < 0) {
                score = 0;
            }
            if (score > 1) {
                score = 1;
            }
        }

        public static Score pass(String judgeName, double score, double weight, String reasoning) {
            return new Score(judgeName, true, score, weight, reasoning);
        }

        public static Score fail(String judgeName, double score, double weight, String reasoning) {
            return new Score(judgeName, false, score, weight, reasoning);
        }
    }

    public record AggregateMetrics(
            int totalCases,
            int passed,
            int failed,
            int errored,
            double passRate,
            double avgLatencyMs,
            double p95LatencyMs,
            int routeAccuracy,
            int toolCallAccuracy,
            int safetyPassRate
    ) {
        public static AggregateMetrics empty() {
            return new AggregateMetrics(0, 0, 0, 0, 0.0, 0.0, 0.0, 0, 0, 0);
        }
    }
}
