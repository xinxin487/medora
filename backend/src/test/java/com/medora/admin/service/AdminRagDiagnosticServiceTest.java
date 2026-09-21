package com.medora.admin.service;

import com.medora.knowledge.service.RetrievalResult;
import com.medora.knowledge.service.RetrievalService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdminRagDiagnosticServiceTest {
    @Test
    void returnsOnlyBoundedSnippetAndStageScores() {
        RetrievalService retrieval = mock(RetrievalService.class);
        when(retrieval.retrieve("取消挂号", "FAQ", 5)).thenReturn(List.of(
                new RetrievalResult("doc-1", 42L, 3, "挂号规则", "FAQ", .42,
                        "预约/取消", "bounded snippet", "private parent context")
        ));

        var response = new AdminRagDiagnosticService(retrieval).diagnose("取消挂号", "FAQ", 5);

        assertThat(response.appliedFilters()).containsEntry("bizScope", "FAQ");
        assertThat(response.items()).singleElement().satisfies(item -> {
            assertThat(item.childChunkId()).isEqualTo(42L);
            assertThat(item.snippet()).isEqualTo("bounded snippet");
            assertThat(item).hasNoNullFieldsOrPropertiesExcept("parentChunkId");
        });
    }
}
