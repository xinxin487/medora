# Medora M2 Layered RAG Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build structure-aware Parent-Child indexing, filtered hybrid retrieval, real reranking, exact citations, and reproducible quality reports.

**Architecture:** Store parent and child chunks in the existing chunk table using a self-reference. Retrieve only children through filtered FTS and pgvector pools, fuse with RRF, rerank a larger candidate set, and expand parent context only after final selection.

**Tech Stack:** PostgreSQL FTS, pgvector, Flyway, existing EmbeddingService/AgentModelGateway, Jackson, JUnit 5, Testcontainers.

**Spec:** `docs/superpowers/specs/2026-09-21-medora-agent-platform-evolution-design.md`

## Global Constraints

- Never return unpublished, expired, unauthorized, or metadata-mismatched documents to reranking.
- Preserve old chunks until new index output passes parity; gate reads with `medora.evolution.layered-rag`.
- Reranker failure returns deterministic RRF order.
- Citations identify the child evidence actually injected, even when parent context is appended.
- Do not call a real model in normal Maven tests.

## Review Focus

- An oversized FAQ/list/table must not be split in the middle of one logical record.
- Duplicate lexical/vector hits must appear once after fusion.
- Malformed reranker output must not reorder or drop candidates.
- Expired and wrong-department documents must be absent before vector/keyword scoring.
- Parent expansion must remain inside the configured token budget.

---

### Task 1: Add hierarchical chunk and metadata persistence

**Files:**
- Create: `backend/src/main/resources/db/migration/V18__layered_knowledge_chunks.sql`
- Modify: `backend/src/main/java/com/medora/knowledge/domain/KnowledgeChunkEntity.java`
- Modify: `backend/src/main/java/com/medora/knowledge/domain/KnowledgeDocumentEntity.java`
- Modify: `backend/src/main/java/com/medora/knowledge/repository/KnowledgeChunkRepository.java`
- Test: `backend/src/test/java/com/medora/knowledge/repository/LayeredChunkMigrationTest.java`

**Interfaces:**
- Consumes: V7–V9 knowledge schema.
- Produces: `chunk_type`, `parent_chunk_id`, `token_count`, `content_hash`, `page_number`, and document metadata/effective-date columns.

- [ ] **Step 1: Write migration assertions**

Create a legacy document/chunk, run Flyway, and assert legacy rows become `CHILD`, have a nonblank hash, and remain searchable. Assert the self-FK rejects a missing parent.

- [ ] **Step 2: Verify failure**

Run: `cd backend && mvn -Dtest=LayeredChunkMigrationTest test`

Expected: FAIL for missing columns.

- [ ] **Step 3: Implement V18 and entity mappings**

Add indexes for `(document_id,chunk_type,status)`, `parent_chunk_id`, and effective/status metadata. Use enum-like constants `PARENT` and `CHILD` but keep database fields as varchar for compatibility. Add factory methods `parent(...)` and `child(parent,...)` rather than exposing setters.

- [ ] **Step 4: Run migration tests**

Run: `cd backend && mvn -Dtest=LayeredChunkMigrationTest,MedoraBackendApplicationTests test`

Expected: PASS and Hibernate validate succeeds.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/resources/db/migration/V18__layered_knowledge_chunks.sql backend/src/main/java/com/medora/knowledge/domain backend/src/main/java/com/medora/knowledge/repository backend/src/test/java/com/medora/knowledge/repository/LayeredChunkMigrationTest.java
git commit -m "feat: add hierarchical knowledge chunk schema"
```

### Task 2: Implement structure-aware Parent-Child semantic chunking

**Files:**
- Modify: `backend/src/main/java/com/medora/knowledge/chunk/DocumentChunk.java`
- Modify: `backend/src/main/java/com/medora/knowledge/chunk/DocumentChunker.java`
- Create: `backend/src/main/java/com/medora/knowledge/chunk/DocumentSection.java`
- Create: `backend/src/main/java/com/medora/knowledge/chunk/StructureAwareSplitter.java`
- Create: `backend/src/main/java/com/medora/knowledge/chunk/SemanticBoundarySplitter.java`
- Modify: `backend/src/main/java/com/medora/knowledge/service/DocumentIngestService.java`
- Test: `backend/src/test/java/com/medora/knowledge/chunk/HierarchicalDocumentChunkerTest.java`

**Interfaces:**
- Consumes: parsed document text and `EmbeddingService` sentence vectors.
- Produces: ordered `DocumentChunk` records with `chunkType`, `parentOrdinal`, `sectionPath`, `content`, `tokenCount`, and `contentHash`.

- [ ] **Step 1: Write structure tests**

Use one fixture containing two Markdown headings, a three-row table, a list, and two FAQ pairs. Assert each child belongs to one parent, FAQ Q/A stays together, table rows stay together, child token count is within 180–350 except indivisible records, and parent content covers all children.

- [ ] **Step 2: Write semantic-boundary and fallback tests**

Stub adjacent similarities `[.91,.88,.31,.87]`; assert the split occurs at `.31`. Throw from EmbeddingService and assert the rule-based result is still nonempty and ordered.

- [ ] **Step 3: Verify failures**

Run: `cd backend && mvn -Dtest=HierarchicalDocumentChunkerTest test`

Expected: FAIL because hierarchical splitters do not exist.

- [ ] **Step 4: Implement minimal split pipeline**

Structure parsing runs first. Create parents at 800–1200 estimated tokens and children at 180–350 with 40-token overlap. Use the greatest similarity drop below a configurable threshold as a preferred boundary. Hash normalized UTF-8 content with SHA-256. `DocumentIngestService` persists parents first, then children, and embeds children only.

- [ ] **Step 5: Run chunk and ingest tests**

Run: `cd backend && mvn -Dtest=DocumentChunkerTest,HierarchicalDocumentChunkerTest test`

Expected: PASS; existing FAQ tests remain green.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/medora/knowledge/chunk backend/src/main/java/com/medora/knowledge/service/DocumentIngestService.java backend/src/test/java/com/medora/knowledge/chunk
git commit -m "feat: add semantic parent child chunking"
```

### Task 3: Add filtered dual retrieval and RRF fusion

**Files:**
- Create: `backend/src/main/java/com/medora/knowledge/retriever/RetrievalFilter.java`
- Create: `backend/src/main/java/com/medora/knowledge/retriever/RankedCandidate.java`
- Create: `backend/src/main/java/com/medora/knowledge/retriever/ReciprocalRankFusion.java`
- Modify: `backend/src/main/java/com/medora/knowledge/retriever/KeywordRetriever.java`
- Modify: `backend/src/main/java/com/medora/knowledge/retriever/VectorRetriever.java`
- Modify: `backend/src/main/java/com/medora/knowledge/retriever/HybridRetriever.java`
- Modify: `backend/src/main/java/com/medora/knowledge/repository/KnowledgeVectorSearchRepository.java`
- Modify: `backend/src/main/java/com/medora/knowledge/config/RagProperties.java`
- Test: `backend/src/test/java/com/medora/knowledge/retriever/ReciprocalRankFusionTest.java`
- Test: `backend/src/test/java/com/medora/knowledge/retriever/FilteredHybridRetrieverTest.java`

**Interfaces:**
- Consumes: query, `RetrievalFilter(documentType,departmentCode,hospitalCode,tags,accessScopes,at)`, and `candidateK`.
- Produces: unique child candidates with lexical rank, vector rank, and RRF score.

- [ ] **Step 1: Write RRF duplicate test**

```java
@Test void fusesAndDeduplicatesByChunkId() {
    List<RankedCandidate> result = rrf.fuse(keyword(a,b), vector(b,c), 60);
    assertThat(result).extracting(RankedCandidate::chunkId).containsExactly(b.id(), a.id(), c.id());
    assertThat(result).filteredOn(x -> x.chunkId().equals(b.id())).hasSize(1);
}
```

- [ ] **Step 2: Write prefilter integration test**

Seed active/current respiratory, expired respiratory, and active cardiology documents. Query with respiratory filter and assert SQL results include only the active/current respiratory child in both retrieval branches.

- [ ] **Step 3: Verify failures**

Run: `cd backend && mvn -Dtest=ReciprocalRankFusionTest,FilteredHybridRetrieverTest test`

Expected: FAIL before RRF/filter implementation.

- [ ] **Step 4: Implement filter-first SQL and RRF**

Use `1.0 / (60 + rank)` for each branch. Query only `chunk_type='CHILD'`. Configure `keyword-candidate-k=20`, `vector-candidate-k=20`, `rerank-candidate-k=20`, and keep final topK separate.

- [ ] **Step 5: Run retrieval tests**

Run: `cd backend && mvn -Dtest=ReciprocalRankFusionTest,FilteredHybridRetrieverTest,RetrievalServiceTest test`

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/medora/knowledge/retriever backend/src/main/java/com/medora/knowledge/repository/KnowledgeVectorSearchRepository.java backend/src/main/java/com/medora/knowledge/config/RagProperties.java backend/src/test/java/com/medora/knowledge/retriever
git commit -m "feat: add filtered rrf hybrid retrieval"
```

### Task 4: Implement schema-validated LLM reranking with deterministic fallback

**Files:**
- Create: `backend/src/main/java/com/medora/knowledge/retriever/LlmReranker.java`
- Create: `backend/src/main/java/com/medora/knowledge/retriever/RerankDecision.java`
- Modify: `backend/src/main/java/com/medora/knowledge/retriever/RerankService.java`
- Modify: `backend/src/main/java/com/medora/knowledge/retriever/NoopReranker.java`
- Modify: `backend/src/main/java/com/medora/knowledge/retriever/HybridRetriever.java`
- Test: `backend/src/test/java/com/medora/knowledge/retriever/LlmRerankerTest.java`

**Interfaces:**
- Consumes: query and at most 20 `RankedCandidate`s.
- Produces: ordered decisions `(chunkId,relevance,reason)`; returns original RRF order on timeout, empty, unknown IDs, duplicate IDs, or invalid JSON.

- [ ] **Step 1: Write success and every fallback test**

Stub valid JSON to reverse two candidates. Separately stub timeout, malformed JSON, an unknown ID, duplicate IDs, and omitted IDs; each invalid case must equal original input order.

- [ ] **Step 2: Verify failures**

Run: `cd backend && mvn -Dtest=LlmRerankerTest test`

Expected: FAIL for missing reranker.

- [ ] **Step 3: Implement strict parsing and fallback**

Send only candidate IDs and bounded snippets. Parse with Jackson into records, require each input ID exactly once, clamp scores to `[0,1]`, and never throw to `HybridRetriever`. Record `SUCCESS`, `TIMEOUT`, or `INVALID_RESPONSE` metrics.

- [ ] **Step 4: Run tests**

Run: `cd backend && mvn -Dtest=LlmRerankerTest,RetrievalServiceTest test`

Expected: PASS with no external network.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/medora/knowledge/retriever backend/src/test/java/com/medora/knowledge/retriever/LlmRerankerTest.java
git commit -m "feat: add resilient llm reranking"
```

### Task 5: Expand parents, emit exact citations, and generate evaluation reports

**Files:**
- Modify: `backend/src/main/java/com/medora/knowledge/retriever/RetrievalResultAssembler.java`
- Modify: `backend/src/main/java/com/medora/knowledge/service/RetrievalResult.java`
- Modify: `backend/src/main/java/com/medora/knowledge/service/RetrievalService.java`
- Create: `backend/src/test/resources/eval/cases/rag-layered-v1.yaml`
- Create: `backend/src/test/java/com/medora/eval/rag/RagEvaluationRunner.java`
- Create: `backend/src/test/java/com/medora/eval/rag/RagMetrics.java`
- Create: `backend/src/main/java/com/medora/admin/controller/AdminRagDiagnosticController.java`
- Create: `backend/src/main/java/com/medora/admin/service/AdminRagDiagnosticService.java`
- Create: `frontend/app/admin/rag/page.tsx`
- Test: `backend/src/test/java/com/medora/knowledge/service/ParentExpansionTest.java`
- Test: `backend/src/test/java/com/medora/eval/rag/RagEvaluationRunnerTest.java`

**Interfaces:**
- Consumes: reranked child results and parent repository lookup.
- Produces: evidence with child citation plus bounded parent context and reports containing Recall@5, MRR@5, nDCG@5, refusal accuracy, citation hit rate, latency, and prompt-token estimate.

- [ ] **Step 1: Write parent budget and citation test**

Assert the cited chunk ID is the child ID, `context` includes the relevant parent excerpt, and combined evidence never exceeds the supplied token budget.

- [ ] **Step 2: Add at least twelve fixed evaluation cases**

Include FAQ, synonym, cross-paragraph, no-answer, expired, department-filtered, conflicting-version, parent-completion, lexical-only, vector-only, hybrid-win, and duplicate-content cases. Every case contains expected document/chunk IDs or explicit no-answer.

- [ ] **Step 3: Implement report calculation**

Write JSON and Markdown to a caller-supplied directory. Tests use a JUnit temp directory and assert exact metric values on a small fixture; do not write source-tree files during Maven test.

- [ ] **Step 4: Run M2 suite**

Run: `cd backend && mvn -Dtest='*Chunk*,*Retriever*,*Retrieval*,*RagEvaluation*' test`

Expected: PASS.

- [ ] **Step 5: Add sanitized RAG diagnostics**

Expose an ADMIN-only test-query endpoint returning document/child/parent IDs, section/page, applied filters, lexical/vector ranks, RRF/rerank scores, timing, and fallback reason. The frontend page submits query plus allow-listed filters and renders the stage table; it must not expose document content beyond the same bounded citation snippet already available to users. Run `cd frontend && npm run build`.

- [ ] **Step 6: Run one report with the layered flag on**

Run: `cd backend && mvn -DskipTests test-compile exec:java -Dexec.classpathScope=test -Dexec.mainClass=com.medora.eval.rag.RagEvaluationRunner -Dexec.args='../docs/reports/m2-rag'`

Expected: `docs/reports/m2-rag.json` and `.md` are generated with all named metrics.

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/com/medora/knowledge backend/src/main/java/com/medora/admin frontend/app/admin/rag backend/src/test/java/com/medora/eval/rag backend/src/test/java/com/medora/knowledge backend/src/test/resources/eval/cases/rag-layered-v1.yaml docs/reports/m2-rag.*
git commit -m "feat: complete layered rag retrieval and evaluation"
```

## M2 Completion Gate

Accept when V18 migrates cleanly, old retrieval remains available with the flag off, layered retrieval filters before scoring, reranker failure preserves RRF, citations point to children, and the generated comparison report includes all seven agreed metrics.
