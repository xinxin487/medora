package com.medora.memory.service;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class MemoryGovernanceMigrationTest {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg16");

    @Test
    void migrationBackfillsGovernanceColumns() {
        flyway("16").migrate();
        var jdbc = new org.springframework.jdbc.core.JdbcTemplate(
                new org.springframework.jdbc.datasource.DriverManagerDataSource(
                        postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()));
        Long userId = jdbc.queryForObject("""
                insert into user_account (email, password_hash, status, register_source)
                values ('migration@example.test', 'hash', 'ACTIVE', 'TEST') returning id
                """, Long.class);
        jdbc.update("""
                insert into user_memory_profile
                    (user_id, memory_type, memory_key, summary, value_json, confidence, source, status)
                values (?, 'PREFERRED_PERIOD', 'default', '上午', '{}'::jsonb, 0.80, 'USER_EXPLICIT', 'ACTIVE')
                """, userId);

        flyway(null).migrate();

        Map<String, Object> row = jdbc.queryForMap("""
                select scope_key, normalized_value, fingerprint, evidence_count
                from user_memory_profile limit 1
                """);
        assertThat(row.get("scope_key")).isEqualTo("GLOBAL");
        assertThat(row.get("normalized_value")).isNotNull();
        assertThat(row.get("fingerprint")).isNotNull();
        assertThat(row.get("evidence_count")).isEqualTo(1);
    }

    private Flyway flyway(String target) {
        var configure = Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration");
        if (target != null) {
            configure.target(target);
        }
        return configure.load();
    }
}
