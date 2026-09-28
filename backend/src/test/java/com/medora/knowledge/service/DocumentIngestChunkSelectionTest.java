package com.medora.knowledge.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medora.ai.config.AiProperties;
import com.medora.ai.embedding.EmbeddingService;
import com.medora.common.config.EvolutionProperties;
import com.medora.knowledge.chunk.DocumentChunk;
import com.medora.knowledge.chunk.DocumentChunker;
import com.medora.knowledge.chunk.SemanticBoundarySplitter;
import com.medora.knowledge.chunk.StructureAwareSplitter;
import com.medora.knowledge.config.RagProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentIngestChunkSelectionTest {

    @Test void layeredFlagSelectsSemanticParentChildPipeline() {
        DocumentIngestService service = service(true);

        List<DocumentChunk> chunks = service.selectChunks("# 挂号\n\n取消挂号请提前办理。");

        assertThat(chunks).anyMatch(chunk -> "PARENT".equals(chunk.chunkType()));
        assertThat(chunks).filteredOn(chunk -> "CHILD".equals(chunk.chunkType()))
                .allMatch(chunk -> chunk.parentOrdinal() >= 0);
    }

    @Test void disabledLayeredFlagKeepsLegacyFlatChunks() {
        DocumentIngestService service = service(false);

        List<DocumentChunk> chunks = service.selectChunks("# 挂号\n\n取消挂号请提前办理。");

        assertThat(chunks).isNotEmpty().allMatch(chunk -> "CHILD".equals(chunk.chunkType()));
        assertThat(chunks).allMatch(chunk -> chunk.parentOrdinal() == -1);
    }

    private DocumentIngestService service(boolean layered) {
        AiProperties ai = new AiProperties();
        EmbeddingService embeddings = new EmbeddingService(ai, new ObjectMapper());
        StructureAwareSplitter structure = new StructureAwareSplitter(
                new SemanticBoundarySplitter(embeddings), new RagProperties());
        return new DocumentIngestService(new DocumentChunker(ai), structure,
                null, null, null, null, embeddings, null,
                new EvolutionProperties(false, layered, false, false, false));
    }
}
