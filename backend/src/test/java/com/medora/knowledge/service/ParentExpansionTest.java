package com.medora.knowledge.service;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ParentExpansionTest {
    @Test void keepsChildCitationAndCapsParentContext() {
        var evidence = new ParentContextExpander().expand(42L, "child evidence", "parent context that completes the child", 20);
        assertThat(evidence.citedChildId()).isEqualTo(42L);
        assertThat(evidence.context().length()).isLessThanOrEqualTo(20);
    }
}
