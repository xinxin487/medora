# Medora A-Stage Agent Platform Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver the A-stage portfolio-ready Agent, RAG, appointment/A2UI, Tool Gateway, and MCP loops defined in the approved design.

**Architecture:** Keep the Spring Boot modular monolith and Next.js application. Add explicit module boundaries and adapters so PostgreSQL remains the source of truth, Redis remains rebuildable acceleration, ADK receives one assembled context, and every Agent tool call passes through one gateway.

**Tech Stack:** Java 21, Spring Boot 3.3.5, Google ADK Java 0.3.0, PostgreSQL/pgvector, Redis, MinIO, Flyway, Micrometer, Next.js 15, React 19, A2UI v0.9.1 subset, MCP Java SDK with Streamable HTTP.

**Spec:** `docs/superpowers/specs/2026-09-21-medora-agent-platform-evolution-design.md`

## Global Constraints

- Implement A stage only; do not implement the optional B-stage hardening roadmap.
- Keep a modular monolith; do not introduce Kafka, Kubernetes, or new business microservices.
- PostgreSQL is the source of truth; Redis locks, caches, expiry indexes, and ZSets must be rebuildable.
- Agents may prepare high-risk writes but may not confirm them; appointment confirmation is a deterministic user action.
- Do not modify existing Flyway migrations `V1` through `V16`; add monotonically numbered migrations.
- Keep external-model tests deterministic in CI by using stubs or recordings.
- Pin A2UI behavior to the accepted v0.9.1 subset and reject components outside the local catalog.
- Pin and test one MCP Java SDK/protocol combination; use Streamable HTTP rather than legacy SSE transport.
- Preserve the user's untracked `deploy/compose/docker-compose.dev.yml` and `deploy/docker/postgres-pgvector.Dockerfile`; review their content before any M6 edit.

## Review Focus

- A Redis outage must not permit overselling or prevent database-backed booking; covered in M4 Task 4.
- Repeated, contradictory, expired, and irrelevant memories must not leak into ADK context; covered in M1 Tasks 2 and 4.
- Reranker timeout or malformed JSON must preserve deterministic RRF results; covered in M2 Task 4.
- Forged A2UI action IDs, tokens, user IDs, and slot parameters must never reach the appointment commit path; covered in M4 Tasks 3 and 5.
- A dead or malicious MCP endpoint must not block local tools or allow arbitrary URL access; covered in M5 Tasks 2 and 4.

---

## Plan Set and Dependency Order

Execute these plans in order. Each milestone must pass its own completion gate before the next starts.

1. [M0 Baseline and Safety Rails](2026-09-21-medora-m0-baseline-plan.md)
2. [M1 Context Builder and Memory Governance](2026-09-21-medora-m1-context-memory-plan.md)
3. [M2 Layered RAG](2026-09-21-medora-m2-layered-rag-plan.md)
4. [M3 Tool Gateway Core](2026-09-21-medora-m3-tool-gateway-plan.md)
5. [M4 A2UI Appointment and Waitlist](2026-09-21-medora-m4-a2ui-appointment-plan.md)
6. [M5 MCP Server and Client](2026-09-21-medora-m5-mcp-plan.md)
7. [M6 Portfolio Delivery](2026-09-21-medora-m6-portfolio-delivery-plan.md)

## Locked File Structure

New backend packages are grouped by responsibility:

```text
com.medora.agent.context       context profiles, budgets, assembly, diagnostics
com.medora.memory.governance  normalization, fingerprints, conflicts, selection
com.medora.knowledge.chunk    hierarchical and semantic chunking
com.medora.toolgateway        descriptors, policies, execution, adapters, audit
com.medora.a2ui               protocol surfaces, actions, catalog validation
com.medora.appointment.hold   temporary reservation state and expiry
com.medora.appointment.waitlist waitlist state, Redis index, promotion
com.medora.mcp                MCP client/server adapters and configuration
```

Frontend additions:

```text
frontend/components/a2ui      allow-listed renderer components
frontend/lib/a2ui             protocol types, reducer, action client
frontend/app/admin/tools      tool health and invocation diagnostics
frontend/app/admin/context    context-selection diagnostics
frontend/app/admin/rag        retrieval diagnostics
```

MCP demo service:

```text
mcp/hospital-directory        independent Java MCP server and container
```

Do not create generic `utils`, `helpers`, or `manager` dumping-ground classes. Each file named in a milestone plan has one stated responsibility.

## Program Completion Gate

Run after all milestone gates pass:

```bash
cd backend && mvn clean test
cd ../frontend && npm run build
cd .. && docker compose -f deploy/compose/docker-compose.dev.yml config
```

Expected:

- Maven reports `BUILD SUCCESS` with no failed tests.
- Next.js production build exits 0.
- Docker Compose configuration renders without unresolved required variables.
- M6 smoke script completes all three demo flows and writes sanitized reports under `docs/reports/`.

Commit the program-level documentation only after the milestone documents and generated reports agree with the actual commands.
