package com.medora.knowledge.service;

import com.medora.ai.embedding.EmbeddingService;
import com.medora.ai.embedding.EmbeddingVector;
import com.medora.knowledge.chunk.DocumentChunk;
import com.medora.knowledge.chunk.DocumentChunker;
import com.medora.knowledge.chunk.StructureAwareSplitter;
import com.medora.knowledge.domain.KnowledgeChunkEntity;
import com.medora.knowledge.domain.KnowledgeDocumentEntity;
import com.medora.knowledge.domain.KnowledgeIngestTaskEntity;
import com.medora.knowledge.repository.KnowledgeChunkRepository;
import com.medora.knowledge.repository.KnowledgeDocumentRepository;
import com.medora.knowledge.repository.KnowledgeIngestTaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.CompletableFuture;

@Service
public class DocumentIngestService {

    private static final Logger log = LoggerFactory.getLogger(DocumentIngestService.class);

    private final DocumentChunker documentChunker;
    private final KnowledgeChunkRepository knowledgeChunkRepository;
    private final KnowledgeIngestTaskRepository knowledgeIngestTaskRepository;
    private final KnowledgeDocumentRepository knowledgeDocumentRepository;
    private final KnowledgeJsonService knowledgeJsonService;
    private final EmbeddingService embeddingService;
    private final TransactionTemplate transactionTemplate;

    public DocumentIngestService(DocumentChunker documentChunker,
                                 KnowledgeChunkRepository knowledgeChunkRepository,
                                 KnowledgeIngestTaskRepository knowledgeIngestTaskRepository,
                                 KnowledgeDocumentRepository knowledgeDocumentRepository,
                                 KnowledgeJsonService knowledgeJsonService,
                                 EmbeddingService embeddingService,
                                 TransactionTemplate transactionTemplate) {
        this.documentChunker = documentChunker;
        this.knowledgeChunkRepository = knowledgeChunkRepository;
        this.knowledgeIngestTaskRepository = knowledgeIngestTaskRepository;
        this.knowledgeDocumentRepository = knowledgeDocumentRepository;
        this.knowledgeJsonService = knowledgeJsonService;
        this.embeddingService = embeddingService;
        this.transactionTemplate = transactionTemplate;
    }

    @Async
    public CompletableFuture<Integer> ingestAsync(KnowledgeDocumentEntity document, String parsedContent) {
        try {
            int count = transactionTemplate.execute(status -> ingestInternal(document, parsedContent));
            log.info("Async ingest complete for document {}: {} chunks", document.getPublicId(), count);
            return CompletableFuture.completedFuture(count);
        } catch (Exception ex) {
            log.error("Async ingest failed for document {}: {}", document.getPublicId(), ex.getMessage());
            transactionTemplate.executeWithoutResult(status -> {
                KnowledgeDocumentEntity reloaded = knowledgeDocumentRepository.findById(document.getId()).orElseThrow();
                reloaded.markIngestFailed(ex.getMessage());
                knowledgeDocumentRepository.save(reloaded);
                knowledgeChunkRepository.deleteByDocument(document);
            });
            return CompletableFuture.failedFuture(ex);
        }
    }

    private int ingestInternal(KnowledgeDocumentEntity document, String parsedContent) {
        KnowledgeIngestTaskEntity task = knowledgeIngestTaskRepository.save(
                KnowledgeIngestTaskEntity.running(document, "REINDEX")
        );
        try {
            knowledgeChunkRepository.deleteByDocument(document);
            List<DocumentChunk> chunks = new StructureAwareSplitter().split(parsedContent);
            if (chunks.isEmpty()) {
                chunks = documentChunker.chunk(parsedContent);
            }
            Map<Integer, KnowledgeChunkEntity> parents = new HashMap<>();
            for (DocumentChunk chunk : chunks) {
                if (!"PARENT".equals(chunk.chunkType())) continue;
                parents.put(chunk.parentOrdinal(), knowledgeChunkRepository.save(KnowledgeChunkEntity.parent(document, chunk.index(), chunk.sectionPath(), chunk.content(), metadata(document))));
            }
            for (DocumentChunk chunk : chunks) {
                if ("PARENT".equals(chunk.chunkType())) continue;
                KnowledgeChunkEntity savedChunk = knowledgeChunkRepository.save(KnowledgeChunkEntity.child(document, parents.get(chunk.parentOrdinal()), chunk.index(), chunk.sectionPath(), chunk.content(), metadata(document)));
                EmbeddingVector embedding = embeddingService.embed(chunk.content());
                knowledgeChunkRepository.updateEmbedding(savedChunk.getId(), toVectorLiteral(embedding.values()), embedding.model());
            }
            document = knowledgeDocumentRepository.findById(document.getId()).orElseThrow();
            document.markIndexed();
            knowledgeDocumentRepository.save(document);
            task.markSuccess();
            return chunks.size();
        } catch (RuntimeException ex) {
            document = knowledgeDocumentRepository.findById(document.getId()).orElseThrow();
            document.markIngestFailed(ex.getMessage());
            knowledgeDocumentRepository.save(document);
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

    private String metadata(KnowledgeDocumentEntity document) {
        return knowledgeJsonService.toJson(Map.of("bizScope", document.getBizScope(), "departmentTags", document.getDepartmentTags(), "doctorTags", document.getDoctorTags()));
    }
}
