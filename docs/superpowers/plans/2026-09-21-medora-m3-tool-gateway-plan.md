# Medora M3 Tool Gateway Core Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Route every local Agent tool through one schema-validated, authorized, resilient, audited execution pipeline.

**Architecture:** Adapt existing `ToolDefinition` beans into versioned `ToolDescriptor`s. The gateway owns policy and execution concerns while business tools remain small adapters over application services; ADK sees only normalized results.

**Tech Stack:** Spring Boot, Jackson, JSON Schema 2020-12 validator, Java virtual-thread executor, Semaphore bulkheads, Micrometer, JUnit 5.

**Spec:** `docs/superpowers/specs/2026-09-21-medora-agent-platform-evolution-design.md`

## Global Constraints

- Existing local tool behavior remains available behind the M0 flag during migration.
- Unknown input properties are rejected before a tool executes.
- The gateway never automatically retries non-idempotent tools.
- Audit records store argument hashes and redacted summaries, not raw patient content or credentials.
- High-risk execution returns `CONFIRMATION_REQUIRED`; it does not perform the write.

## Review Focus

- A knowledge Agent calling an appointment tool must be denied before execution.
- An invalid UUID, oversized page size, or unknown property must not reach the business service.
- Timeout/circuit-open states must return normalized retryability without blocking the chat thread.
- Duplicate gateway submissions to non-idempotent tools must execute at most once.
- Audit/metric labels must not include raw tool arguments or user identifiers.

---

### Task 1: Define descriptors and schema-backed registry

**Files:**
- Modify: `backend/pom.xml`
- Create: `backend/src/main/java/com/medora/toolgateway/descriptor/ToolDescriptor.java`
- Create: `backend/src/main/java/com/medora/toolgateway/descriptor/ToolSourceType.java`
- Create: `backend/src/main/java/com/medora/toolgateway/descriptor/ToolRisk.java`
- Create: `backend/src/main/java/com/medora/toolgateway/registry/GatewayToolRegistry.java`
- Create: `backend/src/main/resources/tool-schemas/query-schedule-slots-input.json`
- Create: `backend/src/main/resources/tool-schemas/prepare-appointment-input.json`
- Create: corresponding input/output schema files for all seven existing tools
- Test: `backend/src/test/java/com/medora/toolgateway/registry/GatewayToolRegistryTest.java`

**Interfaces:**
- Consumes: classpath JSON Schema files and existing tool beans.
- Produces: `Optional<ToolDescriptor> find(String namespacedName)` and immutable `Collection<ToolDescriptor> all()`.

- [ ] **Step 1: Add a JSON Schema validator dependency**

Use `com.networknt:json-schema-validator:2.0.4` (the Jackson 2-compatible line) with an explicit `json-schema-validator.version` property in `pom.xml`; configure dialect 2020-12 in the registry rather than relying on library defaults.

- [ ] **Step 2: Write registry collision and immutability tests**

Register `local.queryScheduleSlots@1`; assert lookup succeeds, `all()` is immutable, and a second descriptor with the same namespace/name/version fails startup with a clear exception.

- [ ] **Step 3: Write schema resource tests**

For every current tool name, load both schemas, validate them against the JSON Schema meta-schema, and assert input object schemas set `additionalProperties:false`.

- [ ] **Step 4: Implement descriptor and registry**

`ToolDescriptor` fields exactly match the spec: name, version, description, sourceType, inputSchema, outputSchema, riskLevel, requiredRoles, allowedAgents, timeout, idempotent, requiresConfirmation, and dataClassification. Namespace all local tools as `local.<name>`.

- [ ] **Step 5: Run tests**

Run: `cd backend && mvn -Dtest=GatewayToolRegistryTest test`

Expected: PASS and all schemas load.

- [ ] **Step 6: Commit**

```bash
git add backend/pom.xml backend/src/main/java/com/medora/toolgateway/descriptor backend/src/main/java/com/medora/toolgateway/registry backend/src/main/resources/tool-schemas backend/src/test/java/com/medora/toolgateway/registry
git commit -m "feat: add schema backed tool registry"
```

### Task 2: Implement policy, validation, and normalized results

**Files:**
- Create: `backend/src/main/java/com/medora/toolgateway/execution/GatewayExecutionContext.java`
- Create: `backend/src/main/java/com/medora/toolgateway/execution/GatewayToolRequest.java`
- Create: `backend/src/main/java/com/medora/toolgateway/execution/GatewayToolResult.java`
- Create: `backend/src/main/java/com/medora/toolgateway/execution/GatewayErrorCode.java`
- Create: `backend/src/main/java/com/medora/toolgateway/policy/ToolAuthorizationPolicy.java`
- Create: `backend/src/main/java/com/medora/toolgateway/policy/ToolConfirmationPolicy.java`
- Create: `backend/src/main/java/com/medora/toolgateway/schema/ToolSchemaValidator.java`
- Test: `backend/src/test/java/com/medora/toolgateway/policy/ToolPolicyTest.java`
- Test: `backend/src/test/java/com/medora/toolgateway/schema/ToolSchemaValidatorTest.java`

**Interfaces:**
- Consumes: descriptor, args, and context `(userId,roles,agentName,conversationId,traceId,requestId,confirmationActionId)`.
- Produces: allow/deny/confirmation decisions and normalized error codes.

- [ ] **Step 1: Write authorization tests**

Assert booking Agent + USER may query slots; knowledge Agent cannot prepare appointment; USER cannot call ADMIN tool; missing user context denies all non-public tools.

- [ ] **Step 2: Write schema edge tests**

Validate correct UUID/date/page-size input. Reject unknown keys, blank required strings, invalid UUID, page size above maximum, and wrong primitive type.

- [ ] **Step 3: Verify failures**

Run: `cd backend && mvn -Dtest=ToolPolicyTest,ToolSchemaValidatorTest test`

Expected: FAIL for missing policy/schema classes.

- [ ] **Step 4: Implement policy order**

Authorization runs before confirmation. Schema validation runs before either adapter. A descriptor with `requiresConfirmation=true` returns `CONFIRMATION_REQUIRED` unless the context contains a verified confirmation action; M4 will provide that verifier.

- [ ] **Step 5: Run tests**

Run: `cd backend && mvn -Dtest=ToolPolicyTest,ToolSchemaValidatorTest test`

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/medora/toolgateway/execution backend/src/main/java/com/medora/toolgateway/policy backend/src/main/java/com/medora/toolgateway/schema backend/src/test/java/com/medora/toolgateway
git commit -m "feat: add tool schema and authorization policy"
```

### Task 3: Build resilient execution and local adapter

**Files:**
- Create: `backend/src/main/java/com/medora/toolgateway/adapter/ToolAdapter.java`
- Create: `backend/src/main/java/com/medora/toolgateway/adapter/LocalToolAdapter.java`
- Create: `backend/src/main/java/com/medora/toolgateway/execution/ToolGateway.java`
- Create: `backend/src/main/java/com/medora/toolgateway/execution/ToolIsolationRegistry.java`
- Modify: `backend/src/main/java/com/medora/agent/tool/ToolRegistry.java`
- Test: `backend/src/test/java/com/medora/toolgateway/execution/ToolGatewayTest.java`

**Interfaces:**
- Consumes: `GatewayToolRequest` and registry/policies from Tasks 1–2.
- Produces: `GatewayToolResult execute(GatewayToolRequest request)` with SUCCESS, FAILED, DENIED, TIMEOUT, UNAVAILABLE, or CONFIRMATION_REQUIRED.

- [ ] **Step 1: Write execution-order, timeout, and bulkhead tests**

Use mocks to assert registry → auth → schema → confirmation → adapter → output validation → audit order. Use latches to occupy the per-tool semaphore and assert the next call returns `RATE_LIMITED`. Block an adapter beyond its descriptor timeout and assert `DEPENDENCY_TIMEOUT` without waiting indefinitely.

- [ ] **Step 2: Write retry tests**

An idempotent read marked retryable may execute twice after one transient failure; a non-idempotent write executes exactly once for the same failure.

- [ ] **Step 3: Verify failure**

Run: `cd backend && mvn -Dtest=ToolGatewayTest test`

Expected: FAIL for missing gateway.

- [ ] **Step 4: Implement with bounded isolation**

Use `Executors.newVirtualThreadPerTaskExecutor()` for timed adapter calls and a per-tool `Semaphore`. Keep a small consecutive-failure circuit state with CLOSED/OPEN/HALF_OPEN and configurable cooldown; synchronize transitions per tool. Never submit a write retry.

- [ ] **Step 5: Run tests**

Run: `cd backend && mvn -Dtest=ToolGatewayTest test`

Expected: PASS and no leaked executor threads after context close.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/medora/toolgateway/adapter backend/src/main/java/com/medora/toolgateway/execution backend/src/main/java/com/medora/agent/tool/ToolRegistry.java backend/src/test/java/com/medora/toolgateway/execution
git commit -m "feat: add isolated local tool execution"
```

### Task 4: Migrate ADK tool calls behind the gateway

**Files:**
- Modify: `backend/src/main/java/com/medora/adk/tool/MedoraFunctionTool.java`
- Modify: `backend/src/main/java/com/medora/adk/tool/AdkToolContextResolver.java`
- Modify: `backend/src/main/java/com/medora/adk/tool/ToolSchemaRegistry.java`
- Modify: `backend/src/main/java/com/medora/agent/tool/ToolExecutionContext.java`
- Modify: all files under `backend/src/main/java/com/medora/agent/tool/tools/`
- Test: `backend/src/test/java/com/medora/adk/tool/MedoraFunctionToolTest.java`
- Test: `backend/src/test/java/com/medora/toolgateway/execution/LocalToolMigrationTest.java`

**Interfaces:**
- Consumes: ADK function call and M1 session state.
- Produces: one gateway request; maps normalized result back to ADK response without exposing stack traces.

- [ ] **Step 1: Add flag-off compatibility and flag-on gateway tests**

Assert flag off calls the existing definition once. Assert flag on calls only `ToolGateway`, includes agent/user/roles/conversation/trace/request context, and serializes the standard result.

- [ ] **Step 2: Add migration coverage for all seven tools**

Parameterize tool names and assert each resolves to exactly one `local.*` descriptor and one adapter target. No tool may bypass the gateway when the flag is on.

- [ ] **Step 3: Implement the adapter switch**

Remove schema duplication from `ToolSchemaRegistry` by making it read Gateway descriptors. Keep the old public methods until all ADK tests compile. Make `bookAppointment` return confirmation required in flag-on mode; M4 replaces it with prepare semantics.

- [ ] **Step 4: Run ADK/tool regression tests**

Run: `cd backend && mvn -Dtest='*Tool*,AdkAgentTopologyTest,AdkOrchestratorTest' test`

Expected: PASS in both flag states.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/medora/adk/tool backend/src/main/java/com/medora/agent/tool backend/src/test/java/com/medora/adk/tool backend/src/test/java/com/medora/toolgateway/execution/LocalToolMigrationTest.java
git commit -m "refactor: route adk tools through gateway"
```

### Task 5: Persist sanitized tool audit and expose admin diagnostics

**Files:**
- Create: `backend/src/main/resources/db/migration/V19__tool_gateway_audit.sql`
- Create: `backend/src/main/java/com/medora/toolgateway/audit/ToolInvocationEntity.java`
- Create: `backend/src/main/java/com/medora/toolgateway/audit/ToolInvocationRepository.java`
- Create: `backend/src/main/java/com/medora/toolgateway/audit/ToolAuditService.java`
- Create: `backend/src/main/java/com/medora/admin/controller/AdminToolController.java`
- Create: `backend/src/main/java/com/medora/admin/service/AdminToolQueryService.java`
- Create: `backend/src/main/java/com/medora/admin/api/response/AdminToolItem.java`
- Create: `frontend/app/admin/tools/page.tsx`
- Modify: `backend/src/main/java/com/medora/toolgateway/execution/ToolGateway.java`
- Test: `backend/src/test/java/com/medora/toolgateway/audit/ToolAuditServiceTest.java`
- Test: `backend/src/test/java/com/medora/admin/service/AdminToolQueryServiceTest.java`

**Interfaces:**
- Consumes: completed normalized invocation.
- Produces: sanitized invocation rows and admin registry/health/recent-call views.

- [ ] **Step 1: Write redaction tests**

Pass arguments containing `token`, `password`, `email`, `symptoms`, and a normal `slotId`. Assert stored JSON replaces sensitive values with `***`, stores a SHA-256 request hash, and retains only allow-listed identifiers.

- [ ] **Step 2: Implement V19 and transactional audit**

Store tool name/version/source/risk/status/error code/duration/trace/request/argument hash/redacted summary/created time. Audit failure must be logged and metered but must not change an already completed tool result.

- [ ] **Step 3: Add ADMIN-only endpoints**

Expose registry health and paginated recent invocations under `/api/v1/admin/tools`. Never return raw argument JSON or credentials.

- [ ] **Step 4: Add the tool diagnostics page**

Render descriptor name/version/source/risk/health, allowed Agents, schema, success rate, P95, circuit state, and sanitized recent calls. Do not render raw arguments, secrets, or patient content. Run `cd frontend && npm run build`.

- [ ] **Step 5: Run M3 suite**

Run: `cd backend && mvn -Dtest='*Tool*,*Gateway*' test`

Expected: PASS with both gateway flag states.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/resources/db/migration/V19__tool_gateway_audit.sql backend/src/main/java/com/medora/toolgateway backend/src/main/java/com/medora/admin frontend/app/admin/tools backend/src/test/java/com/medora/toolgateway backend/src/test/java/com/medora/admin
git commit -m "feat: add tool gateway audit and diagnostics"
```

## M3 Completion Gate

Enable `FEATURE_TOOL_GATEWAY=true`; run all ADK and tool tests. Accept when all seven tools use the gateway, forbidden and malformed calls never execute adapters, high-risk calls require confirmation, timeout/bulkhead tests are bounded, and admin diagnostics contain no raw sensitive arguments.
