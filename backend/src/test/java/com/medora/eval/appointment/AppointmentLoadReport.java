package com.medora.eval.appointment;

import com.medora.eval.report.EvidenceReportSupport;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class AppointmentLoadReport {
    private static final String COMMAND = "./scripts/generate-a-stage-reports.sh";

    private AppointmentLoadReport() { }

    public record LoadResult(int attempts, int quota, int successes, int conflicts, int duplicates,
                             int oversellCount, int finalAvailable, int finalReserved, int finalConfirmed,
                             List<Long> latencyMillis, String redisMode) {
        public LoadResult {
            latencyMillis = List.copyOf(latencyMillis);
        }
    }

    public static void main(String[] args) throws Exception {
        writeReport(args.length == 0 ? Path.of("target/eval/a-stage") : Path.of(args[0]));
    }

    public static Map<String, Object> buildReport(LoadResult result) {
        if (result.oversellCount() != 0 || result.successes() > result.quota()) {
            throw new IllegalStateException("oversell invariant failed");
        }
        if (result.finalAvailable() < 0 || result.finalReserved() < 0 || result.finalConfirmed() < 0
                || result.finalAvailable() + result.finalReserved() + result.finalConfirmed() != result.quota()
                || result.successes() != result.finalReserved() + result.finalConfirmed()) {
            throw new IllegalStateException("inventory counters are not balanced");
        }
        if (result.attempts() != result.successes() + result.conflicts() + result.duplicates()) {
            throw new IllegalStateException("attempt counters are not balanced");
        }

        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("attempts", result.attempts());
        metrics.put("quota", result.quota());
        metrics.put("successes", result.successes());
        metrics.put("conflicts", result.conflicts());
        metrics.put("duplicates", result.duplicates());
        metrics.put("oversellCount", result.oversellCount());
        metrics.put("p50Millis", percentile(result.latencyMillis(), .50));
        metrics.put("p95Millis", percentile(result.latencyMillis(), .95));
        metrics.put("finalAvailable", result.finalAvailable());
        metrics.put("finalReserved", result.finalReserved());
        metrics.put("finalConfirmed", result.finalConfirmed());
        metrics.put("redisMode", result.redisMode());
        return EvidenceReportSupport.report(COMMAND, metrics,
                EvidenceReportSupport.deterministicEnvironment("m4-concurrency-fixture-replay"));
    }

    public static void writeReport(Path output) throws Exception {
        Map<String, Object> report = buildReport(fixedM4Result());
        EvidenceReportSupport.writeJson(output.resolve("appointment-load.json"), report);
        Map<?, ?> metrics = (Map<?, ?>) report.get("metrics");
        String markdown = "# Appointment load\n\n"
                + "> Deterministic replay of the M4 100-attempt/10-quota invariant fixture; this is not a hardware benchmark.\n\n"
                + EvidenceReportSupport.metadataMarkdown(report)
                + "| Metric | Value |\n|---|---:|\n"
                + "| Attempts | " + metrics.get("attempts") + " |\n"
                + "| Quota | " + metrics.get("quota") + " |\n"
                + "| Successes | " + metrics.get("successes") + " |\n"
                + "| Conflicts | " + metrics.get("conflicts") + " |\n"
                + "| Duplicates | " + metrics.get("duplicates") + " |\n"
                + "| Oversell | " + metrics.get("oversellCount") + " |\n"
                + "| P50 / P95 (ms) | " + metrics.get("p50Millis") + " / " + metrics.get("p95Millis") + " |\n"
                + "| Final available / reserved / confirmed | " + metrics.get("finalAvailable") + " / "
                + metrics.get("finalReserved") + " / " + metrics.get("finalConfirmed") + " |\n"
                + "| Redis mode | " + metrics.get("redisMode") + " |\n";
        Files.writeString(output.resolve("appointment-load.md"), markdown);
    }

    private static LoadResult fixedM4Result() {
        List<Long> latency = new ArrayList<>();
        for (long value = 1; value <= 100; value++) latency.add(value);
        return new LoadResult(100, 10, 10, 89, 1, 0, 0, 10, 0, latency, "DATABASE_FALLBACK");
    }

    private static long percentile(List<Long> samples, double fraction) {
        if (samples.isEmpty()) return 0;
        List<Long> sorted = new ArrayList<>(samples);
        sorted.sort(Long::compareTo);
        int index = Math.max(0, (int) Math.ceil(fraction * sorted.size()) - 1);
        return sorted.get(index);
    }
}
