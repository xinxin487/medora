package com.medora.eval.rag;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;

class RagEvaluationRunnerTest {
    @Test void calculatesRecallAndMrrAtFive() {
        assertThat(RagMetrics.recallAt5("b", List.of("a","b"))).isEqualTo(1);
        assertThat(RagMetrics.reciprocalRankAt5("b", List.of("a","b"))).isEqualTo(.5);
    }

    @Test void writesNamedMetricsToJsonAndMarkdown(@TempDir Path reportDirectory) throws Exception {
        RagEvaluationRunner.writeReport(reportDirectory);

        assertThat(Files.readString(reportDirectory.resolve("m2-rag.json")))
                .contains("recallAt5", "mrrAt5", "ndcgAt5", "refusalAccuracy", "citationHitRate", "latencyMs", "promptTokenEstimate");
        assertThat(Files.readString(reportDirectory.resolve("m2-rag.md")))
                .contains("Recall@5", "MRR@5", "nDCG@5", "Refusal accuracy", "Citation hit rate");
    }
}
