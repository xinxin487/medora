# Medora M1 Context Builder and Memory Governance Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Assemble one bounded, explainable ADK context and prevent duplicate, contradictory, expired, or irrelevant memories from entering it.

**Architecture:** Normalize and govern memory before selection, then assemble summary, recent turns, memory, optional RAG evidence, and business state into an immutable `AgentContextBundle`. `AdkOrchestrator` injects that bundle into session state behind the M0 feature flag.

**Tech Stack:** Java 21 records, Spring Data JPA, Flyway, Jackson, Google ADK session state, JUnit 5, Mockito.

**Spec:** `docs/superpowers/specs/2026-09-21-medora-agent-platform-evolution-design.md`

## Global Constraints

- Context Profile prepares context but does not replace ADK routing.
- Do not persist raw prompts in diagnostics; store counts, hashes, IDs, reasons, and estimated tokens.
- A memory with medical symptoms must have an expiry; it cannot become a permanent preference.
- New context stays behind `medora.evolution.context-builder` until parity and fallback tests pass.
- Legacy `ContextPruner` and `TokenBudgetPlanner` remain until Task 5 proves the new path.

## Review Focus

- Contradictory single-value preferences must leave exactly one ACTIVE winner.
- The same fact present in summary and memory must be injected once.
- A summary/Redis/RAG provider failure must degrade without failing the chat.
- A 31+ message conversation must preserve early confirmed facts via summary.
- Diagnostics must explain every rejected memory without exposing its raw value.

---

### Task 1: Extend the memory persistence model for governance

**Files:**
- Modify: `backend/pom.xml`
- Create: `backend/src/main/resources/db/migration/V17__memory_governance.sql`
- Modify: `backend/src/main/java/com/medora/memory/domain/UserMemoryProfileEntity.java`
- Modify: `backend/src/main/java/com/medora/agent/trace/AgentTraceEntity.java`
- Modify: `backend/src/main/java/com/medora/memory/service/UserMemoryProfileRepository.java`
- Test: `backend/src/test/java/com/medora/memory/service/MemoryGovernanceMigrationTest.java`

**Interfaces:**
- Consumes: existing `user_memory_profile` rows and unique `(user_id,memory_type,memory_key)` constraint.
- Produces: `scope_key`, `normalized_value`, `fingerprint`, `evidence_count`, `superseded_by_id`, selector indexes, and `agent_trace.context_diagnostic_json` for sanitized replay.

- [ ] **Step 1: Write a Testcontainers migration test**

```java
@Test void migrationBackfillsGovernanceColumns() {
    jdbc.update("insert into user_memory_profile (...) values (...)");
    flyway.migrate();
    Map<String,Object> row = jdbc.queryForMap("select scope_key,fingerprint,evidence_count from user_memory_profile limit 1");
    assertThat(row.get("scope_key")).isNotNull();
    assertThat(row.get("fingerprint")).isNotNull();
    assertThat(row.get("evidence_count")).isEqualTo(1);
}
```

Add test-scoped `org.testcontainers:postgresql` and `org.testcontainers:junit-jupiter` dependencies to `backend/pom.xml`; Spring Boot 3.3.5 dependency management supplies their compatible versions.

- [ ] **Step 2: Verify failure before the migration exists**

Run: `cd backend && mvn -Dtest=MemoryGovernanceMigrationTest test`

Expected: FAIL for missing columns.

- [ ] **Step 3: Add migration and entity fields**

The migration must add nullable columns, backfill stable legacy values, make `fingerprint` and `scope_key` non-null, add an index on `(user_id,status,memory_type,expires_at)`, add non-null default `{}` JSONB diagnostics to `agent_trace`, and preserve the legacy unique constraint during M1. Entity methods must expose `confirmEvidence(...)`, `supersedeWith(...)`, and `isExpired(now)`; services must not set the new fields directly.

- [ ] **Step 4: Run migration and existing memory tests**

Run: `cd backend && mvn -Dtest=MemoryGovernanceMigrationTest,MemoryServiceTest test`

Expected: PASS and Flyway validates V1–V17.

- [ ] **Step 5: Commit**

```bash
git add backend/pom.xml backend/src/main/resources/db/migration/V17__memory_governance.sql backend/src/main/java/com/medora/memory backend/src/main/java/com/medora/agent/trace/AgentTraceEntity.java backend/src/test/java/com/medora/memory/service/MemoryGovernanceMigrationTest.java
git commit -m "feat: add memory governance persistence"
```

### Task 2: Implement normalization, fingerprints, and conflict resolution

**Files:**
- Create: `backend/src/main/java/com/medora/memory/governance/MemoryCandidate.java`
- Create: `backend/src/main/java/com/medora/memory/governance/MemoryCardinality.java`
- Create: `backend/src/main/java/com/medora/memory/governance/MemoryNormalizer.java`
- Create: `backend/src/main/java/com/medora/memory/governance/MemoryFingerprint.java`
- Create: `backend/src/main/java/com/medora/memory/governance/MemoryConflictResolver.java`
- Modify: `backend/src/main/java/com/medora/memory/service/MemoryService.java`
- Test: `backend/src/test/java/com/medora/memory/governance/MemoryNormalizerTest.java`
- Test: `backend/src/test/java/com/medora/memory/governance/MemoryConflictResolverTest.java`

**Interfaces:**
- Consumes: `MemoryCandidate(memoryType,scopeKey,rawValue,summary,confidence,source,sourceReference,occurredAt,expiresAt)`.
- Produces: normalized candidate with `normalizedValue` and SHA-256 `fingerprint`; conflict decision `KEEP_EXISTING`, `REPLACE_EXISTING`, or `COEXIST`.

- [ ] **Step 1: Write normalization and contradiction tests**

```java
@ParameterizedTest
@CsvSource({"上午,AM", "早上,AM", "午前,AM", "下午,PM"})
void normalizesPeriodAliases(String raw, String expected) {
    assertThat(normalizer.normalize(candidate("PREFERRED_PERIOD", raw)).normalizedValue()).isEqualTo(expected);
}

@Test void explicitNewPreferenceSupersedesOldPreference() {
    var decision = resolver.resolve(active("AM", "USER_EXPLICIT", .8), candidate("PM", "USER_EXPLICIT", .8));
    assertThat(decision).isEqualTo(REPLACE_EXISTING);
}

@Test void oneToolQueryCannotReplaceExplicitPreference() {
    var decision = resolver.resolve(active("AM", "USER_EXPLICIT", .8), candidate("PM", "TOOL_QUERY", .3));
    assertThat(decision).isEqualTo(KEEP_EXISTING);
}
```

- [ ] **Step 2: Run tests and verify failure**

Run: `cd backend && mvn -Dtest=MemoryNormalizerTest,MemoryConflictResolverTest test`

Expected: FAIL because governance types do not exist.

- [ ] **Step 3: Implement deterministic governance**

Define source ranks exactly as `USER_EXPLICIT=5`, `BOOK_APPOINTMENT=4`, `REPEATED_SIGNAL=3`, `USER_MENTION=2`, `LLM_EXTRACTED=1`, `TOOL_QUERY=1`. Use `SHA-256(userId|memoryType|scopeKey|normalizedValue)` encoded as lowercase hex. Require expiry for `RECENT_SYMPTOM`; reject that candidate if expiry is absent.

Change `MemoryService.upsertMemory` to normalize first, increment `evidenceCount` on exact fingerprint, and call the conflict resolver for single-value types. Superseded rows become INACTIVE in the same transaction.

- [ ] **Step 4: Run focused and regression tests**

Run: `cd backend && mvn -Dtest=MemoryNormalizerTest,MemoryConflictResolverTest,MemoryServiceTest test`

Expected: PASS; existing appointment-derived memory remains ACTIVE.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/medora/memory backend/src/test/java/com/medora/memory
git commit -m "feat: normalize and resolve memory conflicts"
```

### Task 3: Add context profiles, budget allocation, and immutable bundle types

**Files:**
- Create: `backend/src/main/java/com/medora/agent/context/ContextProfile.java`
- Create: `backend/src/main/java/com/medora/agent/context/ContextProfileResolver.java`
- Create: `backend/src/main/java/com/medora/agent/context/ContextBudget.java`
- Create: `backend/src/main/java/com/medora/agent/context/TokenEstimator.java`
- Create: `backend/src/main/java/com/medora/agent/context/TokenBudgetAllocator.java`
- Create: `backend/src/main/java/com/medora/agent/context/AgentContextBundle.java`
- Test: `backend/src/test/java/com/medora/agent/context/ContextProfileResolverTest.java`
- Test: `backend/src/test/java/com/medora/agent/context/TokenBudgetAllocatorTest.java`

**Interfaces:**
- Consumes: user message, scene type, and configured maximum context tokens.
- Produces: `ContextProfile` and `ContextBudget(system,currentTurns,summary,rag,memory,total)`; `AgentContextBundle` is consumed by Task 5.

- [ ] **Step 1: Write profile and budget tests**

```java
@Test void bookingProfileUsesBusinessLanguageWithoutCallingLlm() {
    assertThat(resolver.resolve("帮我约明天下午呼吸内科", "GENERAL")).isEqualTo(ContextProfile.BOOKING);
}

@Test void allocationNeverExceedsTotal() {
    ContextBudget b = allocator.allocate(ContextProfile.KNOWLEDGE, 8_000);
    assertThat(b.totalAllocated()).isLessThanOrEqualTo(8_000);
    assertThat(b.rag()).isGreaterThan(b.memory());
}
```

- [ ] **Step 2: Verify failure**

Run: `cd backend && mvn -Dtest=ContextProfileResolverTest,TokenBudgetAllocatorTest test`

Expected: FAIL for missing classes.

- [ ] **Step 3: Implement deterministic profile and budgets**

Use explicit keyword groups for BOOKING, TRIAGE, and KNOWLEDGE; ambiguous input returns GENERAL. Implement the accepted default percentages and adjust BOOKING to increase business state at the expense of RAG. `TokenEstimator` uses `ceil(codePointCount / 2.0)` for Chinese-heavy text and `ceil(chars / 4.0)` otherwise, documented as an A-stage estimate.

- [ ] **Step 4: Run tests**

Run: `cd backend && mvn -Dtest=ContextProfileResolverTest,TokenBudgetAllocatorTest test`

Expected: PASS including empty, emoji, mixed Chinese/English, and zero-budget cases.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/medora/agent/context backend/src/test/java/com/medora/agent/context
git commit -m "feat: add context profiles and token budgets"
```

### Task 4: Select memories and assemble provider output with diagnostics

**Files:**
- Create: `backend/src/main/java/com/medora/agent/context/ContextProvider.java`
- Create: `backend/src/main/java/com/medora/agent/context/ContextItem.java`
- Create: `backend/src/main/java/com/medora/agent/context/ContextDecision.java`
- Create: `backend/src/main/java/com/medora/agent/context/ContextBuilder.java`
- Create: `backend/src/main/java/com/medora/agent/context/ConversationCompressor.java`
- Create: `backend/src/main/java/com/medora/memory/governance/MemorySelector.java`
- Modify: `backend/src/main/java/com/medora/memory/service/MemoryService.java`
- Modify: `backend/src/main/java/com/medora/memory/service/ConversationSummaryService.java`
- Modify: `backend/src/main/java/com/medora/chat/cache/RedisContextCacheService.java`
- Test: `backend/src/test/java/com/medora/agent/context/ContextBuilderTest.java`
- Test: `backend/src/test/java/com/medora/agent/context/ConversationCompressorTest.java`
- Test: `backend/src/test/java/com/medora/memory/governance/MemorySelectorTest.java`

**Interfaces:**
- Consumes: `ContextBuildRequest(userId,conversationId,message,sceneType,maxTokens)` and provider results.
- Produces: `AgentContextBundle` plus decisions with `source`, `fingerprint`, `estimatedTokens`, `selected`, and `reasonCode`.

- [ ] **Step 1: Write selector tests for hard filters and score order**

Include ACTIVE vs expired, BOOKING vs KNOWLEDGE, explicit vs tool-query source, summary duplicate, and budget overflow. Assert reason codes `EXPIRED`, `PROFILE_MISMATCH`, `DUPLICATE`, `CONFLICT_LOSER`, and `BUDGET_EXCEEDED`.

- [ ] **Step 2: Write builder degradation test**

```java
@Test void providerFailureDoesNotFailContextBuild() {
    when(summaryProvider.load(any())).thenThrow(new RedisConnectionFailureException("down"));
    AgentContextBundle bundle = builder.build(request);
    assertThat(bundle.currentMessage()).isEqualTo(request.message());
    assertThat(bundle.decisions()).anyMatch(d -> d.reasonCode().equals("PROVIDER_UNAVAILABLE"));
}
```

Also assert KNOWLEDGE requests call the RAG provider, BOOKING requests include business state, and an ambiguous GENERAL request does not eagerly retrieve RAG unless the current message requires evidence.

- [ ] **Step 3: Verify failures**

Run: `cd backend && mvn -Dtest=MemorySelectorTest,ContextBuilderTest test`

Expected: FAIL for missing selector and builder.

- [ ] **Step 4: Implement selection and assembly**

Implement the accepted score weights: relevance `.40`, confidence `.25`, source quality `.20`, freshness `.15`. Deduplicate with normalized fingerprints across summary, recent turns, memory, and RAG. Enforce one single-value memory per type, one last appointment, and two history items per type. `ConversationCompressor` retains the newest turns verbatim and emits structured `goals`, `confirmedFacts`, `openTasks`, and `recentToolResults`; on summarizer failure it keeps a deterministic bounded tail. Catch provider exceptions individually and emit a decision rather than aborting.

- [ ] **Step 5: Run focused tests**

Run: `cd backend && mvn -Dtest=MemorySelectorTest,ContextBuilderTest,ConversationSummaryServiceTest,MemoryServiceTest test`

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/medora/agent/context backend/src/main/java/com/medora/memory backend/src/main/java/com/medora/chat/cache backend/src/test/java/com/medora/agent/context backend/src/test/java/com/medora/memory/governance
git commit -m "feat: assemble filtered agent context"
```

### Task 5: Inject context into ADK and expose diagnostics

**Files:**
- Modify: `backend/src/main/java/com/medora/adk/orchestrator/AdkOrchestrator.java`
- Modify: `backend/src/main/java/com/medora/adk/session/PostgresBackedSessionService.java`
- Modify: `backend/src/main/java/com/medora/adk/agents/AdkAgentTopology.java`
- Create: `backend/src/main/java/com/medora/admin/api/response/AdminContextDiagnosticResponse.java`
- Create: `backend/src/main/java/com/medora/admin/controller/AdminContextDiagnosticController.java`
- Create: `backend/src/main/java/com/medora/admin/service/AdminContextDiagnosticService.java`
- Create: `frontend/app/admin/context/page.tsx`
- Test: `backend/src/test/java/com/medora/adk/orchestrator/AdkContextInjectionTest.java`
- Test: `backend/src/test/java/com/medora/admin/service/AdminContextDiagnosticServiceTest.java`

**Interfaces:**
- Consumes: `AgentContextBundle` from Task 4 and M0 `EvolutionProperties`.
- Produces: ADK state keys `context.profile`, `context.summary`, `context.recent`, `context.memories`, `context.rag`, `context.business`, and sanitized admin diagnostics.

- [ ] **Step 1: Write flag-off and flag-on orchestration tests**

Assert flag off retains the existing recent-30 behavior. Assert flag on invokes `ContextBuilder`, writes all agreed state keys, and does not serialize decision raw values into the trace.

- [ ] **Step 2: Verify failure**

Run: `cd backend && mvn -Dtest=AdkContextInjectionTest test`

Expected: FAIL until the orchestrator accepts the builder.

- [ ] **Step 3: Integrate state and prompt placeholders**

Change Root and specialist instructions to reference the state keys explicitly. On builder exception, log the trace ID, record `DEGRADED`, and use the existing session reconstruction. Do not run both full context paths simultaneously.

- [ ] **Step 4: Add sanitized diagnostics endpoint**

Persist sanitized decisions to `agent_trace.context_diagnostic_json` and expose `GET /api/v1/admin/context-diagnostics/{traceId}` for ADMIN only. Return profile, allocated/used tokens, source counts, reason counts, and item hashes; omit raw messages, memory values, and prompts.

- [ ] **Step 5: Add the context diagnostics page**

Create a trace-ID lookup form and tables for budget, source counts, and rejection reasons. Render only the sanitized response fields and show an explicit degraded/provider-unavailable badge. Run `cd frontend && npm run build` and require a zero exit code.

- [ ] **Step 6: Run the M1 suite**

Run: `cd backend && mvn -Dtest='*Context*,*Memory*,AdkOrchestratorTest,PostgresBackedSessionServiceTest' test`

Expected: PASS, including 31+ message retention, Redis failure, summary failure, contradiction, and duplicate cases.

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/com/medora/adk backend/src/main/java/com/medora/admin frontend/app/admin/context backend/src/test/java/com/medora/adk backend/src/test/java/com/medora/admin
git commit -m "feat: inject governed context into adk"
```

## M1 Completion Gate

Enable `FEATURE_CONTEXT_BUILDER=true`, run the M1 suite, then execute one recorded 31-message scenario. Accept when the answer uses an early confirmed fact, diagnostics show why each memory was selected/rejected, and disabling the flag restores the unchanged M0 path.
