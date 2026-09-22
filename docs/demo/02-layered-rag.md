# Demo 2: layered RAG, metadata filtering, and fallback

## Setup and fixture

Set `FEATURE_LAYERED_RAG=true`, start the infrastructure/backend/frontend, then load fixture `m6-v1`:

```bash
./scripts/seed-demo.sh
```

The seed creates one published/indexed `GUIDE` document, two parent chunks, and two child chunks. No upload is required. To demonstrate upload instead, use `http://localhost:3000/admin/knowledge` or `POST /api/v1/admin/knowledge/documents/upload`, then publish the returned document with `POST /api/v1/admin/knowledge/documents/{documentId}/publish`.

## Child citation and parent context

1. Sign in with an ADMIN account and open `http://localhost:3000/admin/rag`.
2. Select `导诊说明 (GUIDE)`.
3. Query: `持续呼吸困难并伴口唇发紫或意识异常属于危险信号`.
4. Expected visible result: document `呼吸道症状分层就医演示指南`, section `症状判断 > 危险信号`, and the matching child snippet.
5. Verify the exact child-to-parent expansion at the PostgreSQL truth boundary:

```bash
docker compose -f deploy/compose/docker-compose.dev.yml exec -T postgres \
  psql -U postgres -d medora -P pager=off -c "
    select child.id as child_id, child.section_path,
           child.content as child_citation,
           parent.content as expanded_parent_context
    from knowledge_chunk child
    join knowledge_chunk parent on parent.id = child.parent_chunk_id
    join knowledge_document document on document.id = child.document_id
    where document.metadata_json->>'fixtureVersion' = 'm6-v1'
      and child.chunk_type = 'CHILD'
    order by child.chunk_index;"
```

The child row is the citation target; the longer parent row is prompt context.

## Metadata filter

Run the same query twice in `/admin/rag`:

- `GUIDE`: at least the seeded child appears;
- `FAQ`: returns `NO_MATCH` because `biz_scope` is filtered before ranking.

The same contract is available at `POST /api/v1/admin/rag-diagnostics/query` with `{"query":"...","bizScope":"GUIDE","topK":5}`.

## Forced reranker failure and report comparison

The default local bean is `NoopReranker`. The strict `LlmReranker` adapter is verified separately: a malformed/unknown result must preserve original RRF order.

```bash
mvn -f backend/pom.xml -Dtest=LlmRerankerTest,HybridRetrieverTest test
./scripts/generate-a-stage-reports.sh
```

Expected: both tests pass; `docs/reports/rag-evaluation.md` records Recall@5, MRR@5, nDCG@5, refusal accuracy, citation hit rate, fixture version, flags, command, and the explicit `deterministic-fixture-baseline` mode. These fixture metrics are regression evidence, not production accuracy claims.
