ALTER TABLE knowledge_document
    ADD COLUMN effective_from TIMESTAMPTZ,
    ADD COLUMN effective_to TIMESTAMPTZ,
    ADD COLUMN access_scopes TEXT[] NOT NULL DEFAULT ARRAY[]::TEXT[];

ALTER TABLE knowledge_chunk
    ADD COLUMN chunk_type VARCHAR(16),
    ADD COLUMN parent_chunk_id BIGINT,
    ADD COLUMN token_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN content_hash VARCHAR(64),
    ADD COLUMN page_number INTEGER;

UPDATE knowledge_chunk
SET chunk_type = 'CHILD',
    content_hash = encode(digest(content, 'sha256'), 'hex'),
    token_count = greatest(1, ceil(length(content)::numeric / 4)::integer)
WHERE chunk_type IS NULL OR content_hash IS NULL;

ALTER TABLE knowledge_chunk
    ALTER COLUMN chunk_type SET NOT NULL,
    ALTER COLUMN content_hash SET NOT NULL,
    ADD CONSTRAINT fk_knowledge_chunk_parent_chunk
        FOREIGN KEY (parent_chunk_id) REFERENCES knowledge_chunk(id);

CREATE INDEX idx_knowledge_chunk_document_type_status
    ON knowledge_chunk (document_id, chunk_type, status);
CREATE INDEX idx_knowledge_chunk_parent_chunk ON knowledge_chunk (parent_chunk_id);
CREATE INDEX idx_knowledge_document_effective_status
    ON knowledge_document (publish_status, ingest_status, effective_from, effective_to);
