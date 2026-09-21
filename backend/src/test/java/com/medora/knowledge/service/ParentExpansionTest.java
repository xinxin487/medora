package com.medora.knowledge.service;

import com.medora.knowledge.repository.KnowledgeChunkSearchResult;
import com.medora.knowledge.retriever.RetrievalResultAssembler;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

class ParentExpansionTest {
    @Test void keepsChildCitationAndCapsParentContext() {
        var evidence = new ParentContextExpander().expand(42L, "child evidence", "parent context that completes the child", 20);
        assertThat(evidence.citedChildId()).isEqualTo(42L);
        assertThat(evidence.context().length()).isLessThanOrEqualTo(20);
    }

    @Test void assemblesChildCitationWithBoundedParentContext() {
        var row = new KnowledgeChunkSearchResult(42L, 7, "预约/取消", "child evidence", .8,
                UUID.randomUUID(), "规则", "FAQ");

        RetrievalResult result = new RetrievalResultAssembler().assemble(row, "parent context that completes the child", 20);

        assertThat(result.chunkId()).isEqualTo(42L);
        assertThat(result.context()).contains("child evidence");
        assertThat(result.context().length()).isLessThanOrEqualTo(20);
    }
}
