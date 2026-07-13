package com.medora.eval.report;

import com.medora.eval.domain.EvalRun;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

/**
 * Phase 10 — 把 EvalRun 写成人读的 Markdown。
 *
 * <p>结构:
 * <ul>
 *   <li>头部:运行元信息 + 聚合指标</li>
 *   <li>case 列表(按 status 分组:FAILED/ERROR 优先)</li>
 *   <li>失败 case 的 Judge reasoning</li>
 * </ul>
 */
public class MarkdownReportWriter {

    public String toMarkdown(EvalRun run) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Medora Agent Eval Report\n\n");
        sb.append("- runId: `").append(run.runId()).append("`\n");
        sb.append("- commitSha: `").append(run.commitSha()).append("`\n");
        sb.append("- branch: `").append(run.branch()).append("`\n");
        sb.append("- duration: ").append(humanDuration(run.duration())).append("\n");
        sb.append("- judges: ").append(run.config().getOrDefault("judges", "")).append("\n\n");

        EvalRun.AggregateMetrics agg = run.aggregate();
        sb.append("## Aggregate\n\n");
        sb.append("| metric | value |\n");
        sb.append("|--------|-------|\n");
        sb.append("| totalCases | ").append(agg.totalCases()).append(" |\n");
        sb.append("| passed | ").append(agg.passed()).append(" |\n");
        sb.append("| failed | ").append(agg.failed()).append(" |\n");
        sb.append("| errored | ").append(agg.errored()).append(" |\n");
        sb.append("| passRate | ").append(formatPercent(agg.passRate())).append(" |\n");
        sb.append("| avgLatencyMs | ").append((long) agg.avgLatencyMs()).append(" |\n");
        sb.append("| p95LatencyMs | ").append((long) agg.p95LatencyMs()).append(" |\n");
        sb.append("| routeAccuracy | ").append(agg.routeAccuracy()).append("% |\n");
        sb.append("| toolCallAccuracy | ").append(agg.toolCallAccuracy()).append("% |\n");
        sb.append("| safetyPassRate | ").append(agg.safetyPassRate()).append("% |\n\n");

        sb.append("## Failed / Errored Cases\n\n");
        List<EvalRun.CaseResult> bad = run.results().stream()
                .filter(r -> r.status() != EvalRun.CaseResult.Status.PASSED)
                .toList();
        if (bad.isEmpty()) {
            sb.append("_No failures._\n\n");
        } else {
            sb.append("| caseId | category | status | latency(ms) | reason |\n");
            sb.append("|--------|----------|--------|-----------:|--------|\n");
            for (EvalRun.CaseResult r : bad) {
                String reason = primaryReason(r);
                sb.append("| ").append(r.caseId())
                        .append(" | ").append(r.category())
                        .append(" | ").append(r.status())
                        .append(" | ").append(r.actual() != null ? r.actual().latencyMs() : 0)
                        .append(" | ").append(escape(reason))
                        .append(" |\n");
            }
            sb.append("\n");
        }

        sb.append("## Passed Cases\n\n");
        List<EvalRun.CaseResult> ok = run.results().stream()
                .filter(r -> r.status() == EvalRun.CaseResult.Status.PASSED)
                .toList();
        if (ok.isEmpty()) {
            sb.append("_No passed cases._\n");
        } else {
            sb.append("| caseId | category | latency(ms) |\n");
            sb.append("|--------|----------|-----------:|\n");
            for (EvalRun.CaseResult r : ok) {
                sb.append("| ").append(r.caseId())
                        .append(" | ").append(r.category())
                        .append(" | ").append(r.actual() != null ? r.actual().latencyMs() : 0)
                        .append(" |\n");
            }
        }
        return sb.toString();
    }

    public void write(EvalRun run, Path output) throws IOException {
        if (output.getParent() != null) {
            Files.createDirectories(output.getParent());
        }
        Files.writeString(output, toMarkdown(run));
    }

    private String primaryReason(EvalRun.CaseResult r) {
        if (r.status() == EvalRun.CaseResult.Status.ERROR) {
            return r.error() == null ? "(no error message)" : r.error();
        }
        return r.scores().stream()
                .filter(s -> !s.pass())
                .map(s -> "[" + s.judgeName() + "] " + s.reasoning())
                .reduce((a, b) -> a + " ;; " + b)
                .orElse("(no failed judge)");
    }

    private String escape(String value) {
        if (value == null) return "";
        return value.replace("|", "\\|").replace("\n", " ");
    }

    private String humanDuration(Duration d) {
        if (d == null || d.isZero() || d.isNegative()) return "0s";
        long ms = d.toMillis();
        if (ms < 1000) return ms + "ms";
        if (ms < 60_000) return (ms / 1000.0) + "s";
        long s = ms / 1000;
        return (s / 60) + "m" + (s % 60) + "s";
    }

    private String formatPercent(double rate) {
        return String.format("%.1f%%", rate * 100);
    }
}
