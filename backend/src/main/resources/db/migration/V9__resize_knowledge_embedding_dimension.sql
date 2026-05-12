DROP INDEX IF EXISTS idx_knowledge_chunk_embedding_ivfflat;

ALTER TABLE knowledge_chunk
    ALTER COLUMN embedding TYPE vector(1024);

CREATE INDEX idx_knowledge_chunk_embedding_ivfflat
    ON knowledge_chunk USING ivfflat (embedding vector_cosine_ops)
    WITH (lists = 100);
