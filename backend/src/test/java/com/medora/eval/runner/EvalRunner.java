package com.medora.eval.runner;

import com.medora.eval.domain.EvalRun;
import com.medora.eval.domain.TestCase;
import com.medora.eval.judge.Judge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Phase 10 — 评测主流程。
 *
 * <p>步骤:
 * <ol>
 *   <li>加载所有 TestCase(由调用方传入)</li>
 *   <li>对每个 case 通过 AdkHarness 跑出 ActualBehavior</li>
 *   <li>对每个 case 跑所有 Judge 收集 Score</li>
 *   <li>聚合为 EvalRun(含 AggregateMetrics)</li>
 * </ol>
 *
 * <p>本类不依赖 Spring,可以独立运行(由 EvalCli 或测试驱动)。
 */
public class EvalRunner {

    private static final Logger log = LoggerFactory.getLogger(EvalRunner.class);

    private final List<Judge> judges;
    private final AdkHarness harness;

    public EvalRunner(List<Judge> judges, AdkHarness harness) {
        if (judges == null || judges.isEmpty()) {
            throw new IllegalArgumentException("at least one judge is required");
        }
        this.judges = List.copyOf(judges);
        this.harness = harness == null ? StubAdkHarness.constant(EvalRun.ActualBehavior.empty()) : harness;
    }

    public EvalRun run(List<TestCase> cases) {
        Instant startedAt = Instant.now();
        List<EvalRun.CaseResult> results = new ArrayList<>();
        for (TestCase tc : cases) {
            results.add(runOneCase(tc));
        }
        Instant finishedAt = Instant.now();
        EvalRun.AggregateMetrics agg = aggregate(results);
        return new EvalRun(
                UUID.randomUUID(),
                startedAt,
                finishedAt,
                readCommitSha(),
                readBranch(),
                Map.of(
                        "judges", judges.stream().map(Judge::name).reduce((a, b) -> a + "," + b).orElse(""),
                        "caseCount", String.valueOf(cases.size())
                ),
                results,
                agg
        );
    }

    private EvalRun.CaseResult runOneCase(TestCase tc) {
        long start = System.currentTimeMillis();
        EvalRun.ActualBehavior actual;
        try {
            actual = harness.run(tc);
        } catch (Exception ex) {
            log.warn("case {} errored: {}", tc.id(), ex.toString());
            return new EvalRun.CaseResult(
                    tc.id(),
                    tc.category(),
                    EvalRun.CaseResult.Status.ERROR,
                    EvalRun.ActualBehavior.empty(),
                    List.of(),
                    ex.getMessage()
            );
        }
        long elapsed = System.currentTimeMillis() - start;
        EvalRun.ActualBehavior withLatency = new EvalRun.ActualBehavior(
                actual.reply(),
                actual.transferChain(),
                actual.toolEvents(),
                actual.hallucinationRisk(),
                elapsed,
                actual.errorMessage()
        );
        List<EvalRun.Score> scores = new ArrayList<>();
        for (Judge j : judges) {
            try {
                if (j.supports(tc)) {
                    scores.add(j.score(tc, withLatency));
                }
            } catch (RuntimeException ex) {
                log.warn("Judge {} threw on case {}: {}", j.name(), tc.id(), ex.toString());
                scores.add(EvalRun.Score.fail(j.name(), 0.0, 1.0,
                        "judge threw: " + ex.getClass().getSimpleName() + " " + ex.getMessage()));
            }
        }
        EvalRun.CaseResult.Status status = decideStatus(scores);
        return new EvalRun.CaseResult(
                tc.id(),
                tc.category(),
                status,
                withLatency,
                scores,
                null
        );
    }

    private EvalRun.CaseResult.Status decideStatus(List<EvalRun.Score> scores) {
        if (scores.isEmpty()) {
            return EvalRun.CaseResult.Status.PASSED;
        }
        boolean anyFailed = scores.stream().anyMatch(s -> !s.pass());
        return anyFailed ? EvalRun.CaseResult.Status.FAILED : EvalRun.CaseResult.Status.PASSED;
    }

    private EvalRun.AggregateMetrics aggregate(List<EvalRun.CaseResult> results) {
        if (results.isEmpty()) {
            return EvalRun.AggregateMetrics.empty();
        }
        int total = results.size();
        int passed = (int) results.stream().filter(r -> r.status() == EvalRun.CaseResult.Status.PASSED).count();
        int failed = (int) results.stream().filter(r -> r.status() == EvalRun.CaseResult.Status.FAILED).count();
        int errored = (int) results.stream().filter(r -> r.status() == EvalRun.CaseResult.Status.ERROR).count();
        double passRate = total == 0 ? 0 : (double) passed / total;
        double avgLatency = results.stream()
                .mapToLong(r -> r.actual() != null ? r.actual().latencyMs() : 0)
                .average()
                .orElse(0);
        double p95Latency = percentile(
                results.stream()
                        .map(r -> r.actual() != null ? r.actual().latencyMs() : 0L)
                        .sorted()
                        .mapToLong(Long::longValue)
                        .toArray(),
                95);
        int routeAccuracy = computeDimensionPassRate(results, "RouteJudge");
        int toolCallAccuracy = computeDimensionPassRate(results, "ToolCallJudge");
        int safetyPassRate = computeDimensionPassRate(results, "ReplyMatcherJudge");
        return new EvalRun.AggregateMetrics(
                total, passed, failed, errored, passRate, avgLatency, p95Latency,
                routeAccuracy, toolCallAccuracy, safetyPassRate
        );
    }

    private int computeDimensionPassRate(List<EvalRun.CaseResult> results, String judgeName) {
        long relevant = results.stream()
                .filter(r -> r.scores().stream().anyMatch(s -> s.judgeName().equals(judgeName)))
                .count();
        if (relevant == 0) {
            return 0;
        }
        long passed = results.stream()
                .filter(r -> r.scores().stream()
                        .anyMatch(s -> s.judgeName().equals(judgeName) && s.pass()))
                .count();
        return (int) (passed * 100 / relevant);
    }

    private double percentile(long[] sortedAsc, int p) {
        if (sortedAsc.length == 0) return 0;
        int idx = (int) Math.ceil(p / 100.0 * sortedAsc.length) - 1;
        if (idx < 0) idx = 0;
        if (idx >= sortedAsc.length) idx = sortedAsc.length - 1;
        return sortedAsc[idx];
    }

    private String readCommitSha() {
        return System.getenv().getOrDefault("GIT_COMMIT_SHA", "unknown");
    }

    private String readBranch() {
        return System.getenv().getOrDefault("GIT_BRANCH", "unknown");
    }
}
