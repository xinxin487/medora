package com.medora.knowledge.service;

import com.medora.knowledge.config.RagProperties;
import com.medora.knowledge.repository.KnowledgeChunkSearchResult;
import com.medora.knowledge.repository.KnowledgeChunkRepository;
import com.medora.knowledge.retriever.HybridRetriever;
import com.medora.knowledge.retriever.RetrievalResultAssembler;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RetrievalServiceTest {

    private final RagProperties ragProperties = new RagProperties();

    @Test
    void shouldReturnEmptyResultForBlankQuery() {
        RetrievalService service = new RetrievalService(mock(HybridRetriever.class),
                new RetrievalResultAssembler(), ragProperties);

        assertTrue(service.retrieve(" ", null, 5).isEmpty());
    }

    @Test
    void shouldFilterOutIrrelevantVectorOnlyResult() {
        HybridRetriever hybridRetriever = mock(HybridRetriever.class);
        RetrievalService service = new RetrievalService(hybridRetriever,
                new RetrievalResultAssembler(), ragProperties);
        when(hybridRetriever.retrieve("我最近咳嗽，应该挂哪个科？", null, 5))
                .thenReturn(List.of(cancelAppointmentRow()));

        assertTrue(service.retrieve("我最近咳嗽，应该挂哪个科？", null, 5).isEmpty());
    }

    @Test
    void shouldKeepResultWhenQuerySharesChineseKeyTerms() {
        HybridRetriever hybridRetriever = mock(HybridRetriever.class);
        RetrievalService service = new RetrievalService(hybridRetriever,
                new RetrievalResultAssembler(), ragProperties);
        when(hybridRetriever.retrieve("取消挂号", null, 5))
                .thenReturn(List.of(cancelAppointmentRow()));

        List<RetrievalResult> results = service.retrieve("取消挂号", null, 5);

        assertEquals(1, results.size());
        assertEquals("挂号取消规则测试文档", results.getFirst().sectionPath());
    }

    @Test
    void shouldIncludeParentContextWithoutChangingChildCitation() {
        HybridRetriever hybridRetriever = mock(HybridRetriever.class);
        KnowledgeChunkRepository chunks = mock(KnowledgeChunkRepository.class);
        RetrievalService service = new RetrievalService(hybridRetriever, new RetrievalResultAssembler(), ragProperties, chunks);
        when(hybridRetriever.retrieve("取消挂号", null, 5)).thenReturn(List.of(cancelAppointmentRow()));
        when(chunks.findParentContentByChildId(1L)).thenReturn(java.util.Optional.of("完整的退号流程说明"));

        RetrievalResult result = service.retrieve("取消挂号", null, 5).getFirst();

        assertEquals(1L, result.chunkId());
        assertTrue(result.context().contains("完整的退号流程说明"));
    }

    private KnowledgeChunkSearchResult cancelAppointmentRow() {
        return new KnowledgeChunkSearchResult(
                1L,
                0,
                "挂号取消规则测试文档",
                "# 挂号取消规则测试文档 门诊挂号成功后，如需取消，请至少在就诊前一天 18:00 前在线操作。",
                0.45,
                UUID.randomUUID(),
                "phase5-cancel-rule",
                "APPOINTMENT_RULE"
        );
    }
}
