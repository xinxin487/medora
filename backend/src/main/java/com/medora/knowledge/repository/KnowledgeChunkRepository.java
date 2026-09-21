package com.medora.knowledge.repository;

import com.medora.knowledge.domain.KnowledgeChunkEntity;
import com.medora.knowledge.domain.KnowledgeDocumentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface KnowledgeChunkRepository extends JpaRepository<KnowledgeChunkEntity, Long> {

    @Modifying
    void deleteByDocument(KnowledgeDocumentEntity document);

    long countByDocument(KnowledgeDocumentEntity document);

    List<KnowledgeChunkEntity> findByDocumentOrderByChunkIndexAsc(KnowledgeDocumentEntity document);

    List<KnowledgeChunkEntity> findByDocumentAndChunkTypeOrderByChunkIndexAsc(KnowledgeDocumentEntity document, String chunkType);

    @Query(value = """
            select parent.content
            from knowledge_chunk child
            join knowledge_chunk parent on parent.id = child.parent_chunk_id
            where child.id = :childId
            """, nativeQuery = true)
    Optional<String> findParentContentByChildId(@Param("childId") Long childId);

    @Modifying
    @Query(value = """
            update knowledge_chunk
            set embedding = cast(:embedding as vector),
                embedding_model = :embeddingModel
            where id = :chunkId
            """, nativeQuery = true)
    void updateEmbedding(@Param("chunkId") Long chunkId,
                         @Param("embedding") String embedding,
                         @Param("embeddingModel") String embeddingModel);

    @Query(value = """
            select
                c.id as chunkId,
                c.chunk_index as chunkIndex,
                c.section_path as sectionPath,
                c.content as content,
                (
                    ts_rank_cd(c.content_tsv, plainto_tsquery('simple', :ftsQuery)) * 10
                    + case when lower(c.content) like lower(concat('%', :query, '%')) then 5 else 0 end
                    + case when lower(d.title) like lower(concat('%', :query, '%')) then 2 else 0 end
                ) as score,
                d.public_id as documentPublicId,
                d.title as title,
                d.biz_scope as bizScope,
                d.uploaded_at as uploadedAt
            from knowledge_chunk c
            join knowledge_document d on d.id = c.document_id
            where c.status = 'ACTIVE'
              and c.chunk_type = 'CHILD'
              and d.publish_status = 'PUBLISHED'
              and d.ingest_status = 'INDEXED'
              and (d.effective_from is null or d.effective_from <= now())
              and (d.effective_to is null or d.effective_to > now())
              and (:bizScope is null or d.biz_scope = :bizScope)
              and (
                    c.content_tsv @@ plainto_tsquery('simple', :ftsQuery)
                    or lower(c.content) like lower(concat('%', :query, '%'))
                    or lower(d.title) like lower(concat('%', :query, '%'))
              )
            order by score desc, d.uploaded_at desc, c.chunk_index asc
            limit :limit
            """, nativeQuery = true)
    List<KnowledgeChunkSearchRow> searchPublished(@Param("query") String query,
                                                  @Param("ftsQuery") String ftsQuery,
                                                  @Param("bizScope") String bizScope,
                                                  @Param("limit") int limit);
}
