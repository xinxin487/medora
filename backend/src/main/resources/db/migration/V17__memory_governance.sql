ALTER TABLE user_memory_profile
    ADD COLUMN scope_key VARCHAR(128),
    ADD COLUMN normalized_value TEXT,
    ADD COLUMN fingerprint CHAR(64),
    ADD COLUMN evidence_count INTEGER NOT NULL DEFAULT 1,
    ADD COLUMN superseded_by_id BIGINT;

UPDATE user_memory_profile
SET scope_key = 'GLOBAL',
    normalized_value = lower(trim(summary)),
    fingerprint = encode(digest(user_id::text || '|' || memory_type || '|GLOBAL|' || lower(trim(summary)), 'sha256'), 'hex'),
    evidence_count = 1
WHERE scope_key IS NULL OR normalized_value IS NULL OR fingerprint IS NULL;

ALTER TABLE user_memory_profile
    ALTER COLUMN scope_key SET NOT NULL,
    ALTER COLUMN fingerprint SET NOT NULL,
    ADD CONSTRAINT fk_user_memory_profile_superseded_by
        FOREIGN KEY (superseded_by_id) REFERENCES user_memory_profile (id);

CREATE INDEX idx_user_memory_profile_selector
    ON user_memory_profile (user_id, status, memory_type, expires_at);

ALTER TABLE agent_trace
    ADD COLUMN context_diagnostic_json JSONB NOT NULL DEFAULT '{}'::jsonb;
