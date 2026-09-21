# Medora M6 Portfolio Delivery Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the complete A-stage system reproducible, measurable, demonstrable, and understandable from a clean checkout.

**Architecture:** Compose starts all infrastructure and the demo MCP service; deterministic seed and smoke commands reproduce three showcase flows. Generated reports and diagrams derive from the real implementation and test output rather than unsupported claims.

**Tech Stack:** Docker Compose, Bash, Flyway, Maven, Next.js, existing eval runner, Markdown/Mermaid.

**Spec:** `docs/superpowers/specs/2026-09-21-medora-agent-platform-evolution-design.md`

## Global Constraints

- Never commit `.env`, real API keys, JWT secrets, MinIO credentials, access tokens, or patient data.
- Do not overwrite the user's existing untracked Docker files; inspect, merge, and obtain approval on conflicts.
- Demo seed is synthetic and idempotent.
- Every README command must be executed once from a clean database before claiming it works.
- Reports must name their exact command, timestamp, git commit, feature flags, and fixture version.

## Review Focus

- A clean machine with only documented prerequisites must reach the login page and health endpoint.
- Re-running seed/smoke commands must not duplicate users, documents, slots, or appointments.
- Reports must not silently use real-user data or a different feature configuration.
- Diagrams and README endpoints must match implemented routes and names.
- Stopping Redis or the demo MCP server during the documented drill must produce the promised degraded behavior.

---

### Task 1: Finish compose, environment example, and deterministic seed

**Files:**
- Modify: `deploy/compose/docker-compose.dev.yml`
- Modify: `deploy/docker/postgres-pgvector.Dockerfile`
- Create: `.env.example`
- Create: `scripts/seed-demo.sh`
- Create: `scripts/reset-demo-data.sql`
- Modify: `deploy/README.md`
- Test: `scripts/test-seed-idempotency.sh`

**Interfaces:**
- Consumes: Docker Desktop and documented environment values.
- Produces: healthy PostgreSQL/pgvector, Redis, MinIO, hospital-directory MCP, backend dependencies, and deterministic synthetic demo data.

- [ ] **Step 1: Inspect existing untracked deployment files**

Run:

```bash
git status --short deploy
sed -n '1,320p' deploy/compose/docker-compose.dev.yml
sed -n '1,220p' deploy/docker/postgres-pgvector.Dockerfile
```

Preserve user-authored ports, volumes, names, and credentials unless the user approves a conflicting change.

- [ ] **Step 2: Add a safe `.env.example`**

List every required variable with placeholders such as `AI_CHAT_API_KEY=replace-me`; never copy `.env`. Document that the real key is required only for real chat/rerank demos, while test commands use stubs.

- [ ] **Step 3: Implement idempotent synthetic seed**

Seed one demo patient, departments/doctors, dated slots, Parent-Child knowledge fixtures, and no confirmed appointment. Use public IDs/natural fixture keys with upsert semantics. Avoid fixed plaintext production passwords; for local demo document one clearly labeled disposable credential or use the normal registration API.

- [ ] **Step 4: Write and run the double-seed test**

The script seeds twice, queries counts before/after the second run, and fails if any count changes.

Run: `./scripts/test-seed-idempotency.sh`

Expected: exits 0 and prints unchanged counts.

- [ ] **Step 5: Validate compose**

Run: `docker compose -f deploy/compose/docker-compose.dev.yml config`

Expected: exits 0 with no unresolved required variables.

- [ ] **Step 6: Commit**

```bash
git add .env.example deploy scripts/seed-demo.sh scripts/reset-demo-data.sql scripts/test-seed-idempotency.sh
git commit -m "chore: add reproducible a-stage demo environment"
```

### Task 2: Generate Token, RAG, and appointment evidence reports

**Files:**
- Create: `scripts/generate-a-stage-reports.sh`
- Create: `backend/src/test/java/com/medora/eval/context/ContextEfficiencyReport.java`
- Create: `backend/src/test/java/com/medora/eval/appointment/AppointmentLoadReport.java`
- Modify: `backend/src/test/java/com/medora/eval/rag/RagEvaluationRunner.java`
- Create: `docs/reports/README.md`
- Test: `backend/src/test/java/com/medora/eval/context/ContextEfficiencyReportTest.java`
- Test: `backend/src/test/java/com/medora/eval/appointment/AppointmentLoadReportTest.java`

**Interfaces:**
- Consumes: fixed context/RAG/appointment fixtures.
- Produces: JSON and Markdown reports with machine-readable metrics and metadata.

- [ ] **Step 1: Write exact report-schema tests**

Assert every report includes `generatedAt`, `gitCommit`, `fixtureVersion`, `featureFlags`, `command`, `metrics`, and `environment`. Assert sanitizer rejects keys matching token/password/secret/email/message/content.

- [ ] **Step 2: Implement context comparison**

Run the same long conversation through legacy and M1 builders; report estimated total tokens, selected memory count, duplicate count, rejection reasons, and whether the required early fact is present.

- [ ] **Step 3: Implement appointment load summary**

Read the M4 concurrency result and report attempts, quota, successes, conflicts, duplicates, oversell count, P50/P95, final counters, and Redis mode. Fail generation if oversell is nonzero or invariants do not balance.

- [ ] **Step 4: Add one fail-fast report command**

```bash
#!/usr/bin/env bash
set -euo pipefail
(cd backend && mvn -Dtest='*ReportTest,*AppointmentConcurrencyIT' test)
# invoke the three report mains with docs/reports targets
```

- [ ] **Step 5: Generate and inspect reports**

Run: `./scripts/generate-a-stage-reports.sh`

Expected: sanitized `context-efficiency.{json,md}`, `rag-evaluation.{json,md}`, and `appointment-load.{json,md}`.

- [ ] **Step 6: Commit**

```bash
git add scripts/generate-a-stage-reports.sh backend/src/test/java/com/medora/eval docs/reports
git commit -m "docs: add reproducible architecture evidence reports"
```

### Task 3: Add diagrams and three exact demo runbooks

**Files:**
- Create: `docs/architecture/a-stage-overview.md`
- Create: `docs/architecture/context-sequence.md`
- Create: `docs/architecture/rag-sequence.md`
- Create: `docs/architecture/appointment-sequence.md`
- Create: `docs/demo/01-long-conversation.md`
- Create: `docs/demo/02-layered-rag.md`
- Create: `docs/demo/03-a2ui-waitlist.md`
- Modify: `docs/README.md`

**Interfaces:**
- Consumes: implemented endpoint names, reports, and feature flags.
- Produces: architecture maps and copy/paste demo scripts with expected visible evidence.

- [ ] **Step 1: Create diagrams from actual classes/routes**

Use Mermaid component/sequence diagrams. Every node names the concrete class or endpoint, including `ContextBuilder`, `ToolGateway`, `/api/v1/a2ui/actions`, Redis/PostgreSQL truth boundaries, and MCP `/mcp`.

- [ ] **Step 2: Write long-conversation runbook**

Include exact setup, at least 31 deterministic turns or a fixture command, the final question, expected early fact, and where to inspect context decision/token evidence.

- [ ] **Step 3: Write layered-RAG runbook**

Include document upload/seed, query, expected child citation/parent context, metadata filter demonstration, forced reranker failure, and report comparison command.

- [ ] **Step 4: Write A2UI/waitlist runbook**

Include prepare, visible countdown, confirm, duplicate click, full-slot waitlist join, cancellation, candidate offer, and Redis flush/rebuild drill. State expected status/counter after each action.

- [ ] **Step 5: Validate all referenced paths and commands**

Run `rg -n '/api/|FEATURE_|mvn|npm|docker compose' docs/demo docs/architecture` and compare every reference to source/config. Fix mismatches before commit.

- [ ] **Step 6: Commit**

```bash
git add docs/architecture docs/demo docs/README.md
git commit -m "docs: add a-stage architecture and demo runbooks"
```

### Task 4: Add clean-checkout smoke verification and final README

**Files:**
- Create: `scripts/smoke-a-stage.sh`
- Modify: `README.md`
- Modify: `frontend/README.md`
- Modify: `deploy/README.md`
- Create: `docs/a-stage-acceptance-checklist.md`

**Interfaces:**
- Consumes: clean checkout, `.env` created from example, Docker Desktop, JDK 21, Maven 3.9+, Node/npm.
- Produces: one command that verifies infrastructure, backend, frontend, MCP, and the three demo prerequisites.

- [ ] **Step 1: Implement bounded smoke checks**

The script uses `set -euo pipefail`, per-request timeouts, and explicit checks for PostgreSQL/Redis/MinIO/MCP health, backend `/actuator/health`, frontend HTTP, auth registration/login, a stubbed knowledge query, and MCP `tools/list`. It never prints secrets or full response bodies containing user data.

- [ ] **Step 2: Document exact startup order**

README must specify JDK 21, Maven 3.9+, Node version from the project, `npm ci`, `.env` fields the user must supply, compose command, backend command, frontend command, seed, smoke, and report generation.

- [ ] **Step 3: Execute from a clean database**

Use a new named Docker volume or explicit disposable test database; do not delete the user's existing volume. Run startup, seed twice, smoke, backend tests, frontend build, and report generation.

- [ ] **Step 4: Fill acceptance checklist with evidence links**

The checklist records command exit status and links to the three reports. Do not mark an item complete unless its command was run in this checkout.

- [ ] **Step 5: Run final gate**

```bash
./scripts/smoke-a-stage.sh
cd backend && mvn clean test
cd ../frontend && npm run build
cd .. && ./scripts/generate-a-stage-reports.sh
docker compose -f deploy/compose/docker-compose.dev.yml config
```

Expected: every command exits 0.

- [ ] **Step 6: Commit**

```bash
git add README.md frontend/README.md deploy/README.md scripts/smoke-a-stage.sh docs/a-stage-acceptance-checklist.md
git commit -m "docs: finalize a-stage reproducible delivery"
```

## M6 Completion Gate

Accept only after a clean, disposable environment passes seed idempotency, smoke, all backend tests, frontend production build, compose validation, and report generation. The three runbooks must be executable without undocumented manual database edits, and the repository must contain no real secrets or patient data.
