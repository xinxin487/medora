# Medora M5 MCP Server and Client Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Demonstrate real MCP Streamable HTTP discovery and invocation in both directions without bypassing the Tool Gateway security model.

**Architecture:** A gateway MCP adapter discovers allow-listed remote tools and registers namespaced descriptors. Medora separately exposes two read-only local capabilities through an authenticated MCP server; an independent hospital-directory container proves remote discovery rather than loopback method calls.

**Tech Stack:** MCP Java SDK 2.0.0 with Jackson 2 binding, MCP protocol/Streamable HTTP, Spring MVC, existing Tool Gateway, Docker Compose, JUnit 5, Testcontainers/WireMock-style HTTP fixture.

**Spec:** `docs/superpowers/specs/2026-09-21-medora-agent-platform-evolution-design.md`

## Global Constraints

- Pin SDK 2.0.0 and protocol version `2026-07-28`; do not float dependency versions.
- Do not use legacy MCP HTTP+SSE transport.
- Remote endpoint URLs come only from configuration allow-lists; Agents cannot supply a URL.
- Medora exposes only read-only knowledge search and schedule query through MCP in A stage.
- MCP failure must not mark local tools unavailable or block ordinary chat.

## Review Focus

- Arbitrary, localhost-rebound, wrong-origin, or unapproved URLs must be rejected.
- Duplicate remote tool names must be namespaced and cannot overwrite local descriptors.
- A remote response violating the declared output schema must be rejected before entering the LLM.
- Startup with the demo MCP server down must remain healthy but mark only its tools unavailable.
- Authentication tokens must not appear in Agent context, error bodies, logs, audit summaries, or admin responses.

---

### Task 1: Pin MCP dependencies and typed configuration

**Files:**
- Modify: `backend/pom.xml`
- Create: `backend/src/main/java/com/medora/mcp/config/McpProperties.java`
- Create: `backend/src/main/java/com/medora/mcp/config/McpProtocolConfig.java`
- Modify: `backend/src/main/resources/application.yml`
- Test: `backend/src/test/java/com/medora/mcp/config/McpConfigTest.java`

**Interfaces:**
- Consumes: `MCP_ENABLED`, server token, allowed origins, and configured remote server entries.
- Produces: immutable server/client configuration and SDK client/server beans only when M0 MCP flag is enabled.

- [ ] **Step 1: Add pinned SDK modules**

Add `io.modelcontextprotocol.sdk:mcp-core:2.0.0` and `io.modelcontextprotocol.sdk:mcp-json-jackson2:2.0.0`. Use the Servlet Streamable HTTP transport compatible with the existing Spring MVC stack; do not introduce WebFlux.

- [ ] **Step 2: Write disabled/enabled binding tests**

Assert disabled mode creates no MCP transport beans. Enabled mode requires a nonblank server token, at least one allowed origin, and remote entries with `name`, `baseUrl`, `tokenEnvName`, and `allowedTools`.

- [ ] **Step 3: Implement properties and safe validation**

Reject base URLs with user-info, fragments, unsupported scheme, or hosts absent from the explicit allow-list. Store token environment-variable names in configuration; resolve secret values only in the transport factory.
Set and validate the protocol version exactly as `2026-07-28`; a mismatched peer is marked UNAVAILABLE with a sanitized compatibility error.

- [ ] **Step 4: Run config tests and dependency tree**

Run: `cd backend && mvn -Dtest=McpConfigTest test dependency:tree -Dincludes=io.modelcontextprotocol.sdk:*`

Expected: PASS and only version 2.0.0 MCP artifacts.

- [ ] **Step 5: Commit**

```bash
git add backend/pom.xml backend/src/main/java/com/medora/mcp/config backend/src/main/resources/application.yml backend/src/test/java/com/medora/mcp/config
git commit -m "chore: pin and configure mcp sdk"
```

### Task 2: Build independent hospital-directory MCP server

**Files:**
- Create: `mcp/hospital-directory/pom.xml`
- Create: `mcp/hospital-directory/src/main/java/com/medora/mcpdemo/HospitalDirectoryApplication.java`
- Create: `mcp/hospital-directory/src/main/java/com/medora/mcpdemo/FindDepartmentTool.java`
- Create: `mcp/hospital-directory/src/main/resources/departments.json`
- Create: `mcp/hospital-directory/src/test/java/com/medora/mcpdemo/FindDepartmentToolTest.java`
- Create: `mcp/hospital-directory/Dockerfile`
- Create: `mcp/hospital-directory/README.md`

**Interfaces:**
- Consumes: `{symptoms:string}`.
- Produces: `findDepartment` result `{departmentCode,departmentName,reason,disclaimer}` over authenticated Streamable HTTP.

- [ ] **Step 1: Write deterministic tool tests**

Assert cough/breathing maps to respiratory, rash maps to dermatology, unknown input returns general outpatient guidance, blank input fails schema, and output always contains a non-diagnostic disclaimer.

- [ ] **Step 2: Implement the minimal server**

Use the same pinned MCP SDK modules. Bind only to the configured container interface, expose one `/mcp` POST endpoint, validate Bearer token and Origin, and define JSON Schema with `additionalProperties:false`. The mapping is a local JSON fixture, not an LLM call.

- [ ] **Step 3: Run module tests and package**

Run: `cd mcp/hospital-directory && mvn clean test package`

Expected: BUILD SUCCESS and executable artifact/container image input exists.

- [ ] **Step 4: Commit**

```bash
git add mcp/hospital-directory
git commit -m "feat: add hospital directory mcp demo server"
```

### Task 3: Discover remote tools through the Gateway MCP adapter

**Files:**
- Create: `backend/src/main/java/com/medora/mcp/client/McpClientFactory.java`
- Create: `backend/src/main/java/com/medora/mcp/client/McpServerConnection.java`
- Create: `backend/src/main/java/com/medora/mcp/client/McpDiscoveryService.java`
- Create: `backend/src/main/java/com/medora/toolgateway/adapter/McpToolAdapter.java`
- Modify: `backend/src/main/java/com/medora/toolgateway/registry/GatewayToolRegistry.java`
- Test: `backend/src/test/java/com/medora/mcp/client/McpDiscoveryServiceTest.java`
- Test: `backend/src/test/java/com/medora/toolgateway/adapter/McpToolAdapterTest.java`

**Interfaces:**
- Consumes: allow-listed remote configuration and MCP `tools/list`/`tools/call`.
- Produces: descriptors named `mcp.<serverName>.<toolName>` and standard `GatewayToolResult`.

- [ ] **Step 1: Write discovery allow-list/collision tests**

Remote returns allowed `findDepartment`, disallowed `deletePatient`, and name colliding with local tool. Assert only namespaced allowed descriptor registers and local descriptors remain unchanged.

- [ ] **Step 2: Write adapter failure tests**

Cover timeout, HTTP auth failure, invalid JSON-RPC, output-schema violation, oversized response, and connection refusal. Assert normalized UNAVAILABLE/TIMEOUT/FAILED and no raw response enters the result message.

- [ ] **Step 3: Implement discovery and adapter**

Discover at startup and admin refresh, cap tool/schema/response sizes, map remote schemas into `ToolDescriptor`, and pass calls through the existing gateway timeout/bulkhead/audit path. Never register a remote HIGH-risk write in A stage.

- [ ] **Step 4: Run tests**

Run: `cd backend && mvn -Dtest=McpDiscoveryServiceTest,McpToolAdapterTest test`

Expected: PASS with a real local Streamable HTTP fixture, not a direct Java method mock for the protocol boundary.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/medora/mcp/client backend/src/main/java/com/medora/toolgateway backend/src/test/java/com/medora/mcp/client backend/src/test/java/com/medora/toolgateway/adapter
git commit -m "feat: discover and execute remote mcp tools"
```

### Task 4: Expose Medora read-only tools as an authenticated MCP server

**Files:**
- Create: `backend/src/main/java/com/medora/mcp/server/MedoraMcpServerConfig.java`
- Create: `backend/src/main/java/com/medora/mcp/server/McpRequestSecurityFilter.java`
- Create: `backend/src/main/java/com/medora/mcp/server/SearchMedicalKnowledgeMcpTool.java`
- Create: `backend/src/main/java/com/medora/mcp/server/QueryScheduleSlotsMcpTool.java`
- Modify: `backend/src/main/java/com/medora/auth/security/SecurityConfig.java`
- Test: `backend/src/test/java/com/medora/mcp/server/MedoraMcpServerIT.java`

**Interfaces:**
- Consumes: authenticated MCP initialize/list/call requests.
- Produces: only `searchMedicalKnowledge` and `queryScheduleSlots`, backed by low-risk Gateway calls.

- [ ] **Step 1: Write protocol/security integration tests**

Use the SDK client over Streamable HTTP. Assert missing/wrong Bearer token returns unauthorized, disallowed Origin returns 403, allowed client discovers exactly two tools, calls return schema-valid output, and appointment write tools are absent.

- [ ] **Step 2: Implement transport and security boundary**

Mount at `/mcp`, validate Origin before processing, compare token in constant time, cap body/read duration, and build a restricted Gateway execution context with service role and fixed allowed Agent identity. Do not expose stack traces in JSON-RPC errors.

- [ ] **Step 3: Run integration tests**

Run: `cd backend && mvn -Dtest=MedoraMcpServerIT test`

Expected: PASS using real HTTP transport.

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/com/medora/mcp/server backend/src/main/java/com/medora/auth/security/SecurityConfig.java backend/src/test/java/com/medora/mcp/server
git commit -m "feat: expose read only medora mcp tools"
```

### Task 5: Add health state, admin refresh, compose service, and failure isolation test

**Files:**
- Create: `backend/src/main/java/com/medora/mcp/health/McpServerHealth.java`
- Create: `backend/src/main/java/com/medora/mcp/health/McpHealthService.java`
- Modify: `backend/src/main/java/com/medora/admin/controller/AdminToolController.java`
- Modify: `backend/src/main/java/com/medora/admin/service/AdminToolQueryService.java`
- Modify: `deploy/compose/docker-compose.dev.yml`
- Test: `backend/src/test/java/com/medora/mcp/McpFailureIsolationIT.java`

**Interfaces:**
- Consumes: connection/discovery/call outcomes.
- Produces: REGISTERED/HEALTHY/DEGRADED/UNAVAILABLE/DISABLED state and ADMIN-only refresh action.

- [ ] **Step 1: Inspect and preserve the existing untracked compose file**

Run: `git status --short deploy && sed -n '1,260p' deploy/compose/docker-compose.dev.yml`. Confirm with the user before overwriting any conflicting user content; merge the service rather than replacing the file.

- [ ] **Step 2: Write stop/recover isolation test**

Start the demo server, discover/call successfully, stop it, assert only remote descriptors become UNAVAILABLE while a local knowledge tool still succeeds, restart and refresh, then assert HEALTHY and callable again.

- [ ] **Step 3: Implement health and admin refresh**

Expose state and last-success/last-error timestamps without credentials or raw remote bodies. Refresh is ADMIN-only, rate-limited, and re-applies the configured allow-list.

- [ ] **Step 4: Add compose service and run protocol smoke**

Build `hospital-directory-mcp`, give it an internal-only port and health check, and inject its token via environment. Run:

```bash
docker compose -f deploy/compose/docker-compose.dev.yml up -d --build hospital-directory-mcp
cd backend && mvn -Dtest=McpFailureIsolationIT,MedoraMcpServerIT test
```

Expected: PASS; stopping the demo server does not fail local tool tests.

- [ ] **Step 5: Commit only reviewed compose changes**

```bash
git add backend/src/main/java/com/medora/mcp/health backend/src/main/java/com/medora/admin backend/src/test/java/com/medora/mcp deploy/compose/docker-compose.dev.yml
git commit -m "feat: add mcp health and failure isolation"
```

## M5 Completion Gate

Accept when a real MCP client discovers/calls two Medora read-only tools, Medora discovers/calls the external `findDepartment`, protocol/security tests use Streamable HTTP, remote outage leaves local tools healthy, and no logs/admin response contains credentials.
