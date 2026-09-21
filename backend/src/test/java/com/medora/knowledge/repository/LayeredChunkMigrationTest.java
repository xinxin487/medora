package com.medora.knowledge.repository;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class LayeredChunkMigrationTest {
    @Container static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg16");
    @Test void backfillsLegacyChunkAsChildWithHash() {
        flyway("17").migrate();
        var jdbc = new org.springframework.jdbc.core.JdbcTemplate(new org.springframework.jdbc.datasource.DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()));
        Long documentId = jdbc.queryForObject("insert into knowledge_document(title,source_type,biz_scope,publish_status,ingest_status) values('d','MANUAL','GENERAL','PUBLISHED','INDEXED') returning id", Long.class);
        jdbc.update("insert into knowledge_chunk(document_id,chunk_index,content,status) values(?,?,?,?)", documentId, 1, "legacy", "ACTIVE");
        flyway(null).migrate();
        var row = jdbc.queryForMap("select chunk_type, content_hash from knowledge_chunk where document_id=?", documentId);
        assertThat(row.get("chunk_type")).isEqualTo("CHILD");
        assertThat(row.get("content_hash")).isNotNull();
    }
    private Flyway flyway(String target) { var config = Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()).locations("classpath:db/migration"); if (target != null) config.target(target); return config.load(); }
}
