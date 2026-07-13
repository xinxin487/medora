package com.medora.eval.report;

import com.medora.eval.domain.EvalRun;
import com.medora.eval.domain.TestCase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 10 W4 — DiffReporter 单测。
 */
class DiffReporterTest {

    @TempDir
    Path tempDir;

    @Test
    void flagsRegressionWhenBaselinePassedAndCurrentFailed() {
        EvalRun.CaseResult baseline = caseResult("c1", EvalRun.CaseResult.Status.PASSED);
        EvalRun.CaseResult current = caseResult("c1", EvalRun.CaseResult.Status.FAILED);

        DiffReporter.DiffResult diff = new DiffReporter().compare(
                run(List.of(baseline), agg(10, 9, 1, 0)),
                run(List.of(current), agg(10, 8, 2, 0))
        );

        DiffReporter.CaseDiff cd = diff.caseDiffs().stream()
                .filter(d -> "c1".equals(d.caseId()))
                .findFirst().orElseThrow();
        assertThat(cd.isRegression()).isTrue();
        assertThat(cd.isImprovement()).isFalse();
        assertThat(diff.hasRegressions()).isTrue();
    }

    @Test
    void flagsImprovementWhenBaselineFailedAndCurrentPassed() {
        EvalRun.CaseResult baseline = caseResult("c1", EvalRun.CaseResult.Status.FAILED);
        EvalRun.CaseResult current = caseResult("c1", EvalRun.CaseResult.Status.PASSED);

        DiffReporter.DiffResult diff = new DiffReporter().compare(
                run(List.of(baseline), agg(10, 9, 1, 0)),
                run(List.of(current), agg(10, 10, 0, 0))
        );

        DiffReporter.CaseDiff cd = diff.caseDiffs().stream()
                .filter(d -> "c1".equals(d.caseId()))
                .findFirst().orElseThrow();
        assertThat(cd.isImprovement()).isTrue();
        assertThat(cd.isRegression()).isFalse();
        assertThat(diff.hasRegressions()).isFalse();
    }

    @Test
    void flagsNewAndRemovedCases() {
        EvalRun.CaseResult baseline = caseResult("only-base", EvalRun.CaseResult.Status.PASSED);
        EvalRun.CaseResult current = caseResult("only-current", EvalRun.CaseResult.Status.PASSED);

        DiffReporter.DiffResult diff = new DiffReporter().compare(
                run(List.of(baseline), agg(1, 1, 0, 0)),
                run(List.of(current), agg(1, 1, 0, 0))
        );

        DiffReporter.CaseDiff removed = diff.caseDiffs().stream()
                .filter(d -> "only-base".equals(d.caseId())).findFirst().orElseThrow();
        DiffReporter.CaseDiff added = diff.caseDiffs().stream()
                .filter(d -> "only-current".equals(d.caseId())).findFirst().orElseThrow();

        assertThat(removed.isRemovedCase()).isTrue();
        assertThat(added.isNewCase()).isTrue();
    }

    @Test
    void writesMarkdownReport(@TempDir Path outDir) throws Exception {
        EvalRun baseline = run(List.of(caseResult("c1", EvalRun.CaseResult.Status.PASSED)), agg(1, 1, 0, 0));
        EvalRun current = run(List.of(caseResult("c1", EvalRun.CaseResult.Status.FAILED)), agg(1, 0, 1, 0));

        DiffReporter reporter = new DiffReporter();
        DiffReporter.DiffResult diff = reporter.compare(baseline, current);

        Path mdPath = outDir.resolve("diff.md");
        reporter.writeMarkdown(diff, mdPath);

        String md = Files.readString(mdPath);
        assertThat(md).contains("# Medora Eval Diff Report");
        assertThat(md).contains("| REGRESSION | 1 |");
        assertThat(md).contains("c1"); // case id 出现在 regressions 表里
    }

    private static EvalRun.CaseResult caseResult(String id, EvalRun.CaseResult.Status status) {
        return new EvalRun.CaseResult(
                id, TestCase.Category.ROUTING, status,
                EvalRun.ActualBehavior.empty(),
                status == EvalRun.CaseResult.Status.PASSED
                        ? List.of(passScore())
                        : List.of(failScore()),
                null
        );
    }

    private static EvalRun.Score passScore() {
        return new EvalRun.Score("StubJudge", true, 1.0, 1.0, "ok");
    }

    private static EvalRun.Score failScore() {
        return new EvalRun.Score("StubJudge", false, 0.0, 1.0, "regression stub");
    }

    private static EvalRun.AggregateMetrics agg(int total, int passed, int failed, int errored) {
        double rate = total == 0 ? 0 : (double) passed / total;
        return new EvalRun.AggregateMetrics(total, passed, failed, errored, rate, 0, 0, 100, 100, 100);
    }

    private static EvalRun run(List<EvalRun.CaseResult> results, EvalRun.AggregateMetrics agg) {
        Instant now = Instant.now();
        return new EvalRun(
                UUID.randomUUID(), now, now,
                "test-sha", "test-branch",
                Map.of("judges", "StubJudge", "caseCount", String.valueOf(results.size())),
                results, agg
        );
    }
}
