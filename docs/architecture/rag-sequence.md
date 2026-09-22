# Layered RAG sequence

```mermaid
sequenceDiagram
    actor Admin
    participant API as POST /api/v1/admin/rag-diagnostics/query
    participant Service as RetrievalService
    participant Hybrid as HybridRetriever
    participant Keyword as KeywordRetriever
    participant Vector as VectorRetriever
    participant PG as PostgreSQL / pgvector
    participant RRF as ReciprocalRankFusion
    participant Rerank as RerankService
    participant Parent as ParentContextExpander

    Admin->>API: query + bizScope + topK
    API->>Service: diagnose(...)
    Service->>Hybrid: diagnose(query, filter, finalTopK)
    par lexical candidates
        Hybrid->>Keyword: top 20
        Keyword->>PG: FTS + metadata/effective-date filter
    and vector candidates
        Hybrid->>Vector: top 20
        Vector->>PG: pgvector + metadata/effective-date filter
    end
    Hybrid->>RRF: fuse and deduplicate candidates
    Hybrid->>Rerank: rerank up to 20 candidates
    Note over Rerank: malformed/timeout result preserves RRF order
    Hybrid-->>Service: final topK child chunks + stage ranks
    Service->>PG: load parent by child.parent_chunk_id
    Service->>Parent: child snippet + bounded parent context
    Service-->>API: child citation and diagnostics
```

The child chunk remains the citation target. Parent text expands the prompt context but does not replace the child identity. In the local application configuration, `NoopReranker` is the default bean; `LlmReranker` fallback behavior is contract-tested and can be wired to a real provider without changing `HybridRetriever`.
