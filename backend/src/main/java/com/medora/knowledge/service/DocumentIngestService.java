package com.medora.knowledge.service;

import com.medora.ai.embedding.EmbeddingService;
import com.medora.ai.embedding.EmbeddingVector;
import com.medora.knowledge.chunk.DocumentChunk;
import com.medora.knowledge.chunk.DocumentChunker;
import com.medora.knowledge.domain.KnowledgeChunkEntity;
import com.medora.knowledge.domain.KnowledgeDocumentEntity;
import com.medora.knowledge.domain.KnowledgeIngestTaskEntity;
import com.medora.knowledge.repository.KnowledgeChunkRepository;
import com.medora.knowledge.repository.KnowledgeIngestTaskRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class DocumentIngestService {

    private final DocumentChunker documentChunker;
    private final KnowledgeChunkRepository knowledgeChunkRepository;
    private final KnowledgeIngestTaskRepository knowledgeIngestTaskRepository;
    private final KnowledgeJsonService knowledgeJsonService;
    private final EmbeddingService embeddingService;

    public DocumentIngestService(DocumentChunker documentChunker,
                                 KnowledgeChunkRepository knowledgeChunkRepository,
                                 KnowledgeIngestTaskRepository knowledgeIngestTaskRepository,
                                 KnowledgeJsonService knowledgeJsonService,
                                 EmbeddingService embeddingService) {
        this.documentChunker = documentChunker;
        this.knowledgeChunkRepository = knowledgeChunkRepository;
        this.knowledgeIngestTaskRepository = knowledgeIngestTaskRepository;
        this.knowledgeJsonService = knowledgeJsonService;
        this.embeddingService = embeddingService;
    }

    public int ingest(KnowledgeDocumentEntity document, String parsedContent) {
        KnowledgeIngestTaskEntity task = knowledgeIngestTaskRepository.save(
                KnowledgeIngestTaskEntity.running(document, "REINDEX")
        );
        try {
            knowledgeChunkRepository.deleteByDocument(document);
            List<DocumentChunk> chunks = documentChunker.chunk(parsedContent);
            for (DocumentChunk chunk : chunks) {
                EmbeddingVector embedding = embeddingService.embed(chunk.content());
                KnowledgeChunkEntity savedChunk = knowledgeChunkRepository.save(KnowledgeChunkEntity.active(
                        document,
                        chunk.index(),
                        chunk.sectionPath(),
                        chunk.content(),
                        knowledgeJsonService.toJson(Map.of(
                                "bizScope", document.getBizScope(),
                                "departmentTags", document.getDepartmentTags(),
                                "doctorTags", document.getDoctorTags()
                        ))
                ));
                knowledgeChunkRepository.updateEmbedding(savedChunk.getId(), toVectorLiteral(embedding.values()),
                        embedding.model());
            }
            document.markIndexed();
            task.markSuccess();
            return chunks.size();
        } catch (RuntimeException ex) {
            document.markIngestFailed(ex.getMessage());
            task.markFailed(ex.getMessage());
            throw ex;
        }
    }

    public int rebuildEmbeddings(KnowledgeDocumentEntity document) {
        KnowledgeIngestTaskEntity task = knowledgeIngestTaskRepository.save(
                KnowledgeIngestTaskEntity.running(document, "EMBED")
        );
        try {
            List<KnowledgeChunkEntity> chunks = knowledgeChunkRepository.findByDocumentOrderByChunkIndexAsc(document);
            for (KnowledgeChunkEntity chunk : chunks) {
                EmbeddingVector embedding = embeddingService.embed(chunk.getContent());
                knowledgeChunkRepository.updateEmbedding(chunk.getId(), toVectorLiteral(embedding.values()),
                        embedding.model());
            }
            document.markIndexed();
            task.markSuccess();
            return chunks.size();
        } catch (RuntimeException ex) {
            document.markIngestFailed(ex.getMessage());
            task.markFailed(ex.getMessage());
            throw ex;
        }
    }

    private String toVectorLiteral(double[] values) {
        StringBuilder builder = new StringBuilder("[");
        for (int index = 0; index < values.length; index++) {
            if (index > 0) {
                builder.append(',');
            }
            builder.append(values[index]);
        }
        return builder.append(']').toString();
    }
}
