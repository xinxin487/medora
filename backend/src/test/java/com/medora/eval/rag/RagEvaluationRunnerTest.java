package com.medora.eval.rag;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;

class RagEvaluationRunnerTest {
    @Test void calculatesRecallAndMrrAtFive() {
        assertThat(RagMetrics.recallAt5("b", List.of("a","b"))).isEqualTo(1);
        assertThat(RagMetrics.reciprocalRankAt5("b", List.of("a","b"))).isEqualTo(.5);
    }

    @Test void writesNamedMetricsToJsonAndMarkdown(@TempDir Path reportDirectory) throws Exception {
        RagEvaluationRunner.writeReport(reportDirectory);

        Map<String, Object> report = new ObjectMapper().readValue(
                reportDirectory.resolve("rag-evaluation.json").toFile(), new TypeReference<>() { });
        assertThat(report).containsKeys("generatedAt", "gitCommit", "fixtureVersion", "featureFlags",
                "command", "metrics", "environment");
        @SuppressWarnings("unchecked")
        Map<String, Object> metrics = (Map<String, Object>) report.get("metrics");
        assertThat(metrics)
                .containsKeys("recallAt5", "mrrAt5", "ndcgAt5", "refusalAccuracy", "citationHitRate",
                        "latencyMillis", "promptUnitsEstimate", "evaluationMode");
        assertThat(Files.readString(reportDirectory.resolve("rag-evaluation.md")))
                .contains("Recall@5", "MRR@5", "nDCG@5", "Refusal accuracy", "Citation hit rate");
    }

    @Test void calculatesMetricsFromReplayRankingRatherThanExpectedAnswer(@TempDir Path tempDirectory) throws Exception {
        Path expected = tempDirectory.resolve("expected.yaml");
        Path replay = tempDirectory.resolve("replay.yaml");
        Files.writeString(expected, "cases:\n  - id: c1\n    query: q\n    expected: doc-a\n");
        Files.writeString(replay, "cases:\n  - id: c1\n    ranked: [doc-b, doc-a]\n    refused: false\n    citationHit: true\n    latencyMillis: 12\n    promptUnitsEstimate: 8\n");

        Map<String, Object> metrics = RagEvaluationRunner.calculateMetrics(expected, replay);

        assertThat(metrics.get("recallAt5")).isEqualTo(1.0);
        assertThat(metrics.get("mrrAt5")).isEqualTo(0.5);
        assertThat(metrics.get("latencyMillis")).isEqualTo(12.0);
    }
}
