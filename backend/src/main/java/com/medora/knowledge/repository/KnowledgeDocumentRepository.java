package com.medora.knowledge.repository;

import com.medora.knowledge.domain.KnowledgeDocumentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface KnowledgeDocumentRepository
        extends JpaRepository<KnowledgeDocumentEntity, Long>, JpaSpecificationExecutor<KnowledgeDocumentEntity> {

    Optional<KnowledgeDocumentEntity> findByPublicId(UUID publicId);
}
