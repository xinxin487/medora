package com.medora.eval.rag;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class RagEvaluationRunnerTest {
    @Test void calculatesRecallAndMrrAtFive() {
        assertThat(RagMetrics.recallAt5("b", List.of("a","b"))).isEqualTo(1);
        assertThat(RagMetrics.reciprocalRankAt5("b", List.of("a","b"))).isEqualTo(.5);
    }
}
