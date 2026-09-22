package com.medora.eval.context;

import com.medora.agent.context.AgentContextBundle;
import com.medora.agent.context.ContextBuildRequest;
import com.medora.agent.context.ContextBuilder;
import com.medora.agent.context.ContextDecision;
import com.medora.agent.context.ContextItem;
import com.medora.agent.context.ContextProfileResolver;
import com.medora.agent.context.ContextProvider;
import com.medora.agent.context.ConversationCompressor;
import com.medora.agent.context.TokenBudgetAllocator;
import com.medora.agent.context.TokenEstimator;
import com.medora.eval.report.EvidenceReportSupport;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class ContextEfficiencyReport {
    private static final String COMMAND = "./scripts/generate-a-stage-reports.sh";
    private static final String EARLY_FACT = "偏好上午就诊";

    private ContextEfficiencyReport() { }

    public static void main(String[] args) throws Exception {
        writeReport(args.length == 0 ? Path.of("target/eval/a-stage") : Path.of(args[0]));
    }

    public static Map<String, Object> buildReport() {
        TokenEstimator estimator = new TokenEstimator();
        List<String> turns = longConversation();
        String finalQuestion = "请结合已有偏好给出知识说明和下一步建议";

        List<String> legacyTurns = turns.subList(Math.max(0, turns.size() - 30), turns.size());
        int legacyUnits = estimate(estimator, legacyTurns) + estimator.estimate(finalQuestion);
        boolean legacyFactPresent = legacyTurns.stream().anyMatch(turn -> turn.contains(EARLY_FACT));

        ContextProvider memoryProvider = ignored -> new ContextProvider.Result("MEMORY", memoryCandidates(estimator), Map.of());
        ContextBuilder builder = new ContextBuilder(new ContextProfileResolver(), new TokenBudgetAllocator(), List.of(memoryProvider));
        AgentContextBundle bundle = builder.build(new ContextBuildRequest(600L, 601L, finalQuestion, "GENERAL", 600));
        List<String> recentTurns = new ConversationCompressor().compress(turns, 6).recentTurns();
        int m1Units = estimate(estimator, recentTurns) + estimator.estimate(finalQuestion)
                + bundle.decisions().stream().filter(ContextDecision::selected).mapToInt(ContextDecision::estimatedTokens).sum();

        Map<String, Long> rejectionReasons = bundle.decisions().stream()
                .filter(decision -> !decision.selected())
                .collect(Collectors.groupingBy(ContextDecision::reasonCode, LinkedHashMap::new, Collectors.counting()));
        long duplicates = bundle.decisions().stream().filter(decision -> "DUPLICATE".equals(decision.reasonCode())).count();

        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("turnCount", turns.size());
        metrics.put("legacyEstimatedUnits", legacyUnits);
        metrics.put("m1EstimatedUnits", m1Units);
        metrics.put("estimatedUnitsSaved", legacyUnits - m1Units);
        metrics.put("selectedMemoryCount", bundle.memories().size());
        metrics.put("duplicateCount", duplicates);
        metrics.put("rejectionReasons", rejectionReasons);
        metrics.put("legacyEarlyFactPresent", legacyFactPresent);
        metrics.put("m1EarlyFactPresent", bundle.memories().stream().anyMatch(item -> item.contains(EARLY_FACT)));
        return EvidenceReportSupport.report(COMMAND, metrics,
                EvidenceReportSupport.deterministicEnvironment("deterministic-context-fixture"));
    }

    public static void writeReport(Path output) throws Exception {
        Map<String, Object> report = buildReport();
        EvidenceReportSupport.writeJson(output.resolve("context-efficiency.json"), report);
        Map<?, ?> metrics = (Map<?, ?>) report.get("metrics");
        String markdown = "# Context efficiency\n\n"
                + "> Deterministic 34-turn fixture; estimates are A-stage heuristic units, not provider billing.\n\n"
                + EvidenceReportSupport.metadataMarkdown(report)
                + "| Metric | Legacy (last 30) | M1 Context Builder |\n|---|---:|---:|\n"
                + "| Estimated prompt units | " + metrics.get("legacyEstimatedUnits") + " | " + metrics.get("m1EstimatedUnits") + " |\n"
                + "| Required early fact present | " + metrics.get("legacyEarlyFactPresent") + " | " + metrics.get("m1EarlyFactPresent") + " |\n"
                + "| Selected memories | 0 | " + metrics.get("selectedMemoryCount") + " |\n"
                + "| Duplicate candidates removed | 0 | " + metrics.get("duplicateCount") + " |\n\n"
                + "Rejection reasons: `" + metrics.get("rejectionReasons") + "`.\n";
        Files.writeString(output.resolve("context-efficiency.md"), markdown);
    }

    private static List<String> longConversation() {
        List<String> turns = new ArrayList<>();
        turns.add("用户明确说明：" + EARLY_FACT);
        for (int index = 2; index <= 34; index++) {
            turns.add("第" + index + "轮为不影响就诊时段偏好的固定演示话题");
        }
        return List.copyOf(turns);
    }

    private static List<ContextItem> memoryCandidates(TokenEstimator estimator) {
        OffsetDateTime future = OffsetDateTime.parse("2035-01-01T00:00:00+08:00");
        OffsetDateTime expired = OffsetDateTime.parse("2020-01-01T00:00:00+08:00");
        return List.of(
                item("PREFERRED_PERIOD", "period-am", EARLY_FACT, 1.0, "USER_EXPLICIT", future, estimator),
                item("PREFERRED_PERIOD", "period-am", "早上就诊", .6, "TOOL_QUERY", future, estimator),
                item("TEMPORARY_SYMPTOM", "expired-symptom", "短期症状已过期", .9, "USER_EXPLICIT", expired, estimator),
                item("LAST_SUCCESSFUL_APPOINTMENT", "old-appointment", "历史预约", .8, "BOOK_APPOINTMENT", future, estimator));
    }

    private static ContextItem item(String type, String fingerprint, String value, double confidence,
                                    String quality, OffsetDateTime expiry, TokenEstimator estimator) {
        return new ContextItem("MEMORY", type, fingerprint, value, confidence, quality, expiry,
                estimator.estimate(value));
    }

    private static int estimate(TokenEstimator estimator, List<String> values) {
        return values.stream().mapToInt(estimator::estimate).sum();
    }
}
