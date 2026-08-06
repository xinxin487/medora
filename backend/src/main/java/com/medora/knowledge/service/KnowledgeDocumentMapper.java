package com.medora.knowledge.service;

import com.medora.knowledge.api.response.KnowledgeDocumentItem;
import com.medora.knowledge.api.response.KnowledgeDocumentResponse;
import com.medora.knowledge.domain.KnowledgeDocumentEntity;
import com.medora.knowledge.repository.KnowledgeChunkRepository;

final class KnowledgeDocumentMapper {

    private KnowledgeDocumentMapper() {
    }

    static KnowledgeDocumentItem toItem(KnowledgeDocumentEntity document, KnowledgeChunkRepository chunkRepository) {
        return new KnowledgeDocumentItem(
                document.getPublicId().toString(),
                document.getTitle(),
                document.getBizScope(),
                document.getPublishStatus(),
                document.getIngestStatus(),
                chunkRepository.countByDocument(document),
                document.getUploadedAt()
        );
    }

    static KnowledgeDocumentResponse toResponse(KnowledgeDocumentEntity document,
                                                KnowledgeChunkRepository chunkRepository) {
        return new KnowledgeDocumentResponse(
                document.getPublicId().toString(),
                document.getTitle(),
                document.getSourceType(),
                document.getSourceUri(),
                document.getBizScope(),
                document.getPublishStatus(),
                document.getIngestStatus(),
                document.getDepartmentTags(),
                document.getDoctorTags(),
                document.getVersionNo(),
                chunkRepository.countByDocument(document),
                document.getUploadedAt()
        );
    }
}
