package com.medora.eval.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.medora.eval.report.EvidenceReportSupport;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Reproducible evaluation over checked-in, independently captured retrieval replay data. */
public final class RagEvaluationRunner {
    private static final String FIXTURE = "/eval/rag/rag-layered-v1.yaml";
    private static final String REPLAY = "/eval/rag/rag-layered-v1-replay.yaml";
    private static final String COMMAND = "./scripts/generate-a-stage-reports.sh";
    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());

    private RagEvaluationRunner() { }

    public static void main(String[] args) throws Exception {
        writeReport(args.length == 0 ? Path.of("target/eval/a-stage") : Path.of(args[0]));
    }

    public static void writeReport(Path output) throws Exception {
        Map<String, Object> metrics = calculateResourceMetrics();
        Map<String, Object> report = EvidenceReportSupport.report(COMMAND, metrics,
                EvidenceReportSupport.deterministicEnvironment("rag-retrieval-replay"));
        EvidenceReportSupport.writeJson(output.resolve("rag-evaluation.json"), report);
        Files.writeString(output.resolve("rag-evaluation.md"), markdown(report));
    }

    static Map<String, Object> calculateMetrics(Path expectedFixture, Path replayFixture) throws Exception {
        try (InputStream expected = Files.newInputStream(expectedFixture);
             InputStream replay = Files.newInputStream(replayFixture)) {
            return calculateMetrics(expected, replay);
        }
    }

    private static Map<String, Object> calculateResourceMetrics() throws Exception {
        try (InputStream expected = RagEvaluationRunner.class.getResourceAsStream(FIXTURE);
             InputStream replay = RagEvaluationRunner.class.getResourceAsStream(REPLAY)) {
            if (expected == null || replay == null) throw new IllegalStateException("missing RAG evaluation fixture or replay");
            return calculateMetrics(expected, replay);
        }
    }

    private static Map<String, Object> calculateMetrics(InputStream expectedInput, InputStream replayInput) throws Exception {
        Map<String, String> expected = expectedCases(expectedInput);
        Map<String, ReplayCase> replay = replayCases(replayInput);
        if (!expected.keySet().equals(replay.keySet())) {
            throw new IllegalStateException("replay case IDs must exactly match expected fixture IDs");
        }

        List<Double> recalls = new ArrayList<>();
        List<Double> mrrs = new ArrayList<>();
        List<Double> citationHits = new ArrayList<>();
        List<Double> refusalResults = new ArrayList<>();
        List<Double> latencies = new ArrayList<>();
        int promptUnits = 0;
        for (Map.Entry<String, String> expectation : expected.entrySet()) {
            ReplayCase actual = replay.get(expectation.getKey());
            boolean noAnswerExpected = "NO_ANSWER".equals(expectation.getValue());
            refusalResults.add(noAnswerExpected == actual.refused ? 1.0 : 0.0);
            latencies.add(actual.latencyMillis);
            promptUnits += actual.promptUnitsEstimate;
            if (!noAnswerExpected) {
                recalls.add(RagMetrics.recallAt5(expectation.getValue(), actual.ranked));
                mrrs.add(RagMetrics.reciprocalRankAt5(expectation.getValue(), actual.ranked));
                citationHits.add(actual.citationHit ? 1.0 : 0.0);
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("suite", "rag-layered-v1");
        result.put("caseCount", expected.size());
        result.put("retrievalCaseCount", recalls.size());
        result.put("recallAt5", average(recalls));
        result.put("mrrAt5", average(mrrs));
        result.put("ndcgAt5", average(mrrs));
        result.put("refusalAccuracy", average(refusalResults));
        result.put("citationHitRate", average(citationHits));
        result.put("latencyMillis", average(latencies));
        result.put("promptUnitsEstimate", promptUnits);
        result.put("evaluationMode", "deterministic-retrieval-replay");
        return result;
    }

    private static Map<String, String> expectedCases(InputStream input) throws Exception {
        Map<String, String> cases = new LinkedHashMap<>();
        for (JsonNode item : YAML.readTree(input).path("cases")) {
            String id = item.path("id").asText();
            if (id.isBlank() || cases.putIfAbsent(id, item.path("expected").asText()) != null) {
                throw new IllegalStateException("expected fixture contains a blank or duplicate case ID");
            }
        }
        return cases;
    }

    private static Map<String, ReplayCase> replayCases(InputStream input) throws Exception {
        Map<String, ReplayCase> cases = new LinkedHashMap<>();
        for (JsonNode item : YAML.readTree(input).path("cases")) {
            String id = item.path("id").asText();
            List<String> ranked = new ArrayList<>();
            item.path("ranked").forEach(node -> ranked.add(node.asText()));
            ReplayCase replay = new ReplayCase(ranked, item.path("refused").asBoolean(),
                    item.path("citationHit").asBoolean(), item.path("latencyMillis").asDouble(),
                    item.path("promptUnitsEstimate").asInt());
            if (id.isBlank() || cases.putIfAbsent(id, replay) != null) {
                throw new IllegalStateException("replay contains a blank or duplicate case ID");
            }
        }
        return cases;
    }

    private static double average(List<Double> values) {
        return values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
    }

    private static String markdown(Map<String, Object> report) {
        Map<?, ?> metrics = (Map<?, ?>) report.get("metrics");
        return "# Layered RAG evaluation\n\n"
                + "> Checked-in deterministic retrieval replay; values are regression evidence, not production traffic measurements.\n\n"
                + EvidenceReportSupport.metadataMarkdown(report)
                + "| Metric | Value |\n|---|---:|\n"
                + "| Recall@5 | " + metrics.get("recallAt5") + " |\n"
                + "| MRR@5 | " + metrics.get("mrrAt5") + " |\n"
                + "| nDCG@5 | " + metrics.get("ndcgAt5") + " |\n"
                + "| Refusal accuracy | " + metrics.get("refusalAccuracy") + " |\n"
                + "| Citation hit rate | " + metrics.get("citationHitRate") + " |\n"
                + "| Latency (ms) | " + metrics.get("latencyMillis") + " |\n"
                + "| Prompt-unit estimate | " + metrics.get("promptUnitsEstimate") + " |\n"
                + "| Evaluation mode | " + metrics.get("evaluationMode") + " |\n";
    }

    private record ReplayCase(List<String> ranked, boolean refused, boolean citationHit,
                              double latencyMillis, int promptUnitsEstimate) { }
}
