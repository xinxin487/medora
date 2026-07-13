package com.medora.eval.report;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medora.eval.domain.EvalRun;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Phase 10 W4 — Baseline 对比器。
 *
 * <p>把当前 EvalRun 与上一次 commit 的 baseline JSON 对比,输出三类差异:
 * <ul>
 *   <li><b>REGRESSION</b> — baseline 通过、当前失败(最严重)</li>
 *   <li><b>IMPROVEMENT</b> — baseline 失败、当前通过</li>
 *   <li><b>NEW_CASE</b> — 当前有、baseline 没有的 case</li>
 *   <li><b>REMOVED_CASE</b> — baseline 有、当前没有的 case(可能是被删了)</li>
 * </ul>
 *
 * <p>用法:
 * <pre>
 * new DiffReporter().compare(baselineRun, currentRun, Path.of("target/eval/diff.md"));
 * </pre>
 *
 * <p>或者直接读两个 JSON 文件:
 * <pre>
 * new DiffReporter().compareJsonFiles(baselineJson, currentJson, outputMd);
 * </pre>
 *
 * <p>退出语义:有 REGRESSION 或 NEW_CASE(可能影响通过率)时返回 false,CI 可据此决定是否失败。
 */
public class DiffReporter {

    private final ObjectMapper mapper = new ObjectMapper();

    public DiffResult compare(EvalRun baseline, EvalRun current) {
        Map<String, EvalRun.CaseResult> baseMap = indexByCaseId(baseline.results());
        Map<String, EvalRun.CaseResult> currMap = indexByCaseId(current.results());

        List<CaseDiff> caseDiffs = new ArrayList<>();
        for (String id : unionKeys(baseMap, currMap)) {
            EvalRun.CaseResult b = baseMap.get(id);
            EvalRun.CaseResult c = currMap.get(id);
            caseDiffs.add(CaseDiff.of(id, b, c));
        }
        return new DiffResult(
                baseline.runId() == null ? null : baseline.runId().toString(),
                current.runId() == null ? null : current.runId().toString(),
                baseline.aggregate(),
                current.aggregate(),
                caseDiffs
        );
    }

    public DiffResult compareJsonFiles(Path baselineJson, Path currentJson) throws IOException {
        EvalRun baseline = mapper.readValue(Files.readString(baselineJson), EvalRun.class);
        EvalRun current = mapper.readValue(Files.readString(currentJson), EvalRun.class);
        return compare(baseline, current);
    }

    public void writeMarkdown(DiffResult diff, Path output) throws IOException {
        if (output.getParent() != null) {
            Files.createDirectories(output.getParent());
        }
        Files.writeString(output, renderMarkdown(diff));
    }

    private String renderMarkdown(DiffResult diff) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Medora Eval Diff Report\n\n");
        sb.append("- baseline runId: `").append(diff.baselineRunId()).append("`\n");
        sb.append("- current  runId: `").append(diff.currentRunId()).append("`\n\n");

        sb.append("## Aggregate Delta\n\n");
        sb.append("| metric | baseline | current | delta |\n");
        sb.append("|--------|---------:|--------:|------:|\n");
        sb.append(row("passRate",
                diff.baselineAggregate().passRate(),
                diff.currentAggregate().passRate(), true));
        sb.append(row("passed",
                diff.baselineAggregate().passed(),
                diff.currentAggregate().passed(), false));
        sb.append(row("failed",
                diff.baselineAggregate().failed(),
                diff.currentAggregate().failed(), false));
        sb.append(row("errored",
                diff.baselineAggregate().errored(),
                diff.currentAggregate().errored(), false));
        sb.append(row("routeAccuracy",
                diff.baselineAggregate().routeAccuracy(),
                diff.currentAggregate().routeAccuracy(), false));
        sb.append(row("toolCallAccuracy",
                diff.baselineAggregate().toolCallAccuracy(),
                diff.currentAggregate().toolCallAccuracy(), false));
        sb.append(row("safetyPassRate",
                diff.baselineAggregate().safetyPassRate(),
                diff.currentAggregate().safetyPassRate(), false));
        sb.append("\n");

        long regressions = diff.caseDiffs().stream().filter(CaseDiff::isRegression).count();
        long improvements = diff.caseDiffs().stream().filter(CaseDiff::isImprovement).count();
        long newCases = diff.caseDiffs().stream().filter(CaseDiff::isNewCase).count();
        long removed = diff.caseDiffs().stream().filter(CaseDiff::isRemovedCase).count();
        sb.append("## Summary\n\n");
        sb.append("| type | count |\n");
        sb.append("|------|------:|\n");
        sb.append("| REGRESSION | ").append(regressions).append(" |\n");
        sb.append("| IMPROVEMENT | ").append(improvements).append(" |\n");
        sb.append("| NEW_CASE | ").append(newCases).append(" |\n");
        sb.append("| REMOVED_CASE | ").append(removed).append(" |\n\n");

        sb.append("## Regressions\n\n");
        List<CaseDiff> regs = diff.caseDiffs().stream().filter(CaseDiff::isRegression).toList();
        if (regs.isEmpty()) {
            sb.append("_None._\n\n");
        } else {
            sb.append("| caseId | baseline | current | currentReason |\n");
            sb.append("|--------|----------|---------|---------------|\n");
            for (CaseDiff d : regs) {
                sb.append("| ").append(d.caseId())
                        .append(" | ").append(d.baselineStatus())
                        .append(" | ").append(d.currentStatus())
                        .append(" | ").append(escape(d.currentReason())).append(" |\n");
            }
            sb.append("\n");
        }

        sb.append("## Improvements\n\n");
        List<CaseDiff> imps = diff.caseDiffs().stream().filter(CaseDiff::isImprovement).toList();
        if (imps.isEmpty()) {
            sb.append("_None._\n\n");
        } else {
            sb.append("| caseId | baseline | current |\n");
            sb.append("|--------|----------|---------|\n");
            for (CaseDiff d : imps) {
                sb.append("| ").append(d.caseId())
                        .append(" | ").append(d.baselineStatus())
                        .append(" | ").append(d.currentStatus()).append(" |\n");
            }
        }
        return sb.toString();
    }

    private String row(String metric, double baseline, double current, boolean percent) {
        double delta = current - baseline;
        String deltaStr = (delta >= 0 ? "+" : "") + (percent
                ? String.format("%.1f%%", delta * 100)
                : String.format("%.0f", delta));
        String baseStr = percent ? String.format("%.1f%%", baseline * 100) : String.format("%.0f", baseline);
        String currStr = percent ? String.format("%.1f%%", current * 100) : String.format("%.0f", current);
        return "| " + metric + " | " + baseStr + " | " + currStr + " | " + deltaStr + " |\n";
    }

    private String escape(String value) {
        if (value == null) return "";
        return value.replace("|", "\\|").replace("\n", " ");
    }

    private static Map<String, EvalRun.CaseResult> indexByCaseId(List<EvalRun.CaseResult> results) {
        Map<String, EvalRun.CaseResult> map = new HashMap<>();
        if (results == null) return map;
        for (EvalRun.CaseResult r : results) {
            map.put(r.caseId(), r);
        }
        return map;
    }

    private static List<String> unionKeys(Map<String, ?> a, Map<String, ?> b) {
        List<String> keys = new ArrayList<>(a.size() + b.size());
        keys.addAll(a.keySet());
        for (String k : b.keySet()) {
            if (!a.containsKey(k)) keys.add(k);
        }
        keys.sort(String::compareTo);
        return keys;
    }

    public record DiffResult(
            String baselineRunId,
            String currentRunId,
            EvalRun.AggregateMetrics baselineAggregate,
            EvalRun.AggregateMetrics currentAggregate,
            List<CaseDiff> caseDiffs
    ) {
        public boolean hasRegressions() {
            return caseDiffs().stream().anyMatch(CaseDiff::isRegression);
        }
    }

    public record CaseDiff(
            String caseId,
            EvalRun.CaseResult.Status baselineStatus,
            EvalRun.CaseResult.Status currentStatus,
            String baselineReason,
            String currentReason
    ) {
        static CaseDiff of(String id, EvalRun.CaseResult b, EvalRun.CaseResult c) {
            EvalRun.CaseResult.Status bs = b == null ? null : b.status();
            EvalRun.CaseResult.Status cs = c == null ? null : c.status();
            return new CaseDiff(
                    id,
                    bs,
                    cs,
                    b == null ? null : primaryReason(b),
                    c == null ? null : primaryReason(c)
            );
        }

        public boolean isRegression() {
            return baselineStatus == EvalRun.CaseResult.Status.PASSED
                    && currentStatus != null
                    && currentStatus != EvalRun.CaseResult.Status.PASSED;
        }

        public boolean isImprovement() {
            return currentStatus == EvalRun.CaseResult.Status.PASSED
                    && baselineStatus != null
                    && baselineStatus != EvalRun.CaseResult.Status.PASSED;
        }

        public boolean isNewCase() {
            return baselineStatus == null && currentStatus != null;
        }

        public boolean isRemovedCase() {
            return baselineStatus != null && currentStatus == null;
        }

        private static String primaryReason(EvalRun.CaseResult r) {
            if (r.status() == EvalRun.CaseResult.Status.ERROR) {
                return r.error() == null ? "(no error message)" : r.error();
            }
            return r.scores().stream()
                    .filter(s -> !s.pass())
                    .map(s -> "[" + s.judgeName() + "] " + s.reasoning())
                    .reduce((a, b) -> a + " ;; " + b)
                    .orElse("(no failed judge)");
        }
    }
}
