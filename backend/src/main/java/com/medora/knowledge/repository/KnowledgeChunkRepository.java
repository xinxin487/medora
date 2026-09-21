package com.medora.knowledge.repository;

import com.medora.knowledge.domain.KnowledgeChunkEntity;
import com.medora.knowledge.domain.KnowledgeDocumentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface KnowledgeChunkRepository extends JpaRepository<KnowledgeChunkEntity, Long> {

    @Modifying
    void deleteByDocument(KnowledgeDocumentEntity document);

    long countByDocument(KnowledgeDocumentEntity document);

    List<KnowledgeChunkEntity> findByDocumentOrderByChunkIndexAsc(KnowledgeDocumentEntity document);

    List<KnowledgeChunkEntity> findByDocumentAndChunkTypeOrderByChunkIndexAsc(KnowledgeDocumentEntity document, String chunkType);

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
              and d.publish_status = 'PUBLISHED'
              and d.ingest_status = 'INDEXED'
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
