package com.medora.knowledge.repository;

import com.medora.knowledge.domain.KnowledgeDocumentEntity;
import com.medora.knowledge.domain.KnowledgeIngestTaskEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KnowledgeIngestTaskRepository extends JpaRepository<KnowledgeIngestTaskEntity, Long> {
    void deleteByDocument(KnowledgeDocumentEntity document);
}
