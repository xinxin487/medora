package com.medora.knowledge.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Repository
public class KnowledgeVectorSearchRepository {

    private final JdbcTemplate jdbcTemplate;

    public KnowledgeVectorSearchRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<KnowledgeChunkSearchRow> searchPublished(double[] embedding, String bizScope, int limit) {
        if (embedding == null || embedding.length == 0) {
            return List.of();
        }
        return jdbcTemplate.execute((ConnectionCallback<List<KnowledgeChunkSearchRow>>) connection -> {
            String sql = """
                    select
                        c.id as chunk_id,
                        c.chunk_index,
                        c.section_path,
                        c.content,
                        (1 - (c.embedding <=> cast(? as vector))) as score,
                        d.public_id as document_public_id,
                        d.title,
                        d.biz_scope,
                        d.uploaded_at
                    from knowledge_chunk c
                    join knowledge_document d on d.id = c.document_id
                    where c.status = 'ACTIVE'
                      and c.embedding is not null
                      and d.publish_status = 'PUBLISHED'
                      and d.ingest_status = 'INDEXED'
                      and (? is null or d.biz_scope = ?)
                    order by score desc, d.uploaded_at desc, c.chunk_index asc
                    limit ?
                    """;
            try (var ps = connection.prepareStatement(sql)) {
                ps.setString(1, toVectorLiteral(embedding));
                String normalizedBizScope = normalizeToNull(bizScope);
                ps.setString(2, normalizedBizScope);
                ps.setString(3, normalizedBizScope);
                ps.setInt(4, Math.max(limit, 1));
                try (var rs = ps.executeQuery()) {
                    List<KnowledgeChunkSearchRow> results = new ArrayList<>();
                    while (rs.next()) {
                        results.add(new KnowledgeChunkSearchResult(
                                rs.getLong("chunk_id"),
                                rs.getInt("chunk_index"),
                                rs.getString("section_path"),
                                rs.getString("content"),
                                rs.getDouble("score"),
                                UUID.fromString(rs.getString("document_public_id")),
                                rs.getString("title"),
                                rs.getString("biz_scope")
                        ));
                    }
                    return results;
                }
            }
        });
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

    private String normalizeToNull(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase() : null;
    }
}
