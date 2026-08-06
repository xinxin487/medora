CREATE EXTENSION IF NOT EXISTS vector;

ALTER TABLE knowledge_chunk
    ADD COLUMN embedding vector(1024);

CREATE INDEX idx_knowledge_chunk_embedding_ivfflat
    ON knowledge_chunk USING ivfflat (embedding vector_cosine_ops)
    WITH (lists = 100);
