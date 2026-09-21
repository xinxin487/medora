package com.medora.knowledge.retriever;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class ReciprocalRankFusionTest {
    @Test void fusesAndDeduplicatesByChunkId() {
        var a = new RankedCandidate(1L, 0, 0, 0); var b = new RankedCandidate(2L, 0, 0, 0); var c = new RankedCandidate(3L, 0, 0, 0);
        List<RankedCandidate> result = new ReciprocalRankFusion().fuse(List.of(a, b), List.of(b, c), 60);
        assertThat(result).extracting(RankedCandidate::chunkId).containsExactly(2L, 1L, 3L);
        assertThat(result).filteredOn(x -> x.chunkId().equals(2L)).hasSize(1);
    }
}
