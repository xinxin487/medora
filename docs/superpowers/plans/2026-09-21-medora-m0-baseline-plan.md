# Medora M0 Baseline and Safety Rails Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Freeze reproducible Agent, RAG, appointment, build, and observability baselines before behavioral changes begin.

**Architecture:** Add typed feature flags and baseline commands without changing the active ADK path. Capture deterministic evaluation and database-backed appointment behavior so later milestones have measurable before/after evidence.

**Tech Stack:** Spring Boot configuration properties, JUnit 5, Mockito, existing Eval CLI, Micrometer, Maven, Next.js.

**Spec:** `docs/superpowers/specs/2026-09-21-medora-agent-platform-evolution-design.md`

## Global Constraints

- Do not change production behavior in M0.
- All new feature flags default to `false` except diagnostic collection, which may default to `true` if it does not alter responses.
- Do not delete the legacy context classes; removal is decided only after M1 parity tests.
- Baseline reports must not contain access tokens, API keys, email addresses, or raw medical messages.

## Review Focus

- Empty or missing environment variables must still allow local-hash/stub tests to run.
- Metric labels must be bounded enums, never user IDs, trace IDs, prompts, or tool arguments.
- Baseline fixtures must be deterministic across time zones by using fixed clocks.
- Feature flags must bind correctly from both YAML and environment variables.
- The baseline command must fail when any underlying test command fails.

---

### Task 1: Add typed evolution feature flags

**Files:**
- Create: `backend/src/main/java/com/medora/common/config/EvolutionProperties.java`
- Modify: `backend/src/main/java/com/medora/MedoraBackendApplication.java`
- Modify: `backend/src/main/resources/application.yml`
- Test: `backend/src/test/java/com/medora/common/config/EvolutionPropertiesTest.java`

**Interfaces:**
- Consumes: Spring Boot configuration binding.
- Produces: `EvolutionProperties` with `contextBuilder`, `layeredRag`, `toolGateway`, `a2uiAppointment`, and `mcp` booleans.

- [ ] **Step 1: Write the failing binding test**

```java
class EvolutionPropertiesTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class)
            .withPropertyValues("medora.evolution.context-builder=true", "medora.evolution.mcp=true");

    @Test void bindsIndependentFlags() {
        runner.run(context -> {
            EvolutionProperties p = context.getBean(EvolutionProperties.class);
            assertThat(p.contextBuilder()).isTrue();
            assertThat(p.layeredRag()).isFalse();
            assertThat(p.mcp()).isTrue();
        });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(EvolutionProperties.class)
    static class TestConfig {}
}
```

- [ ] **Step 2: Verify the test fails**

Run: `cd backend && mvn -Dtest=EvolutionPropertiesTest test`

Expected: FAIL because `EvolutionProperties` does not exist.

- [ ] **Step 3: Implement the immutable properties record**

```java
@ConfigurationProperties("medora.evolution")
public record EvolutionProperties(
        boolean contextBuilder,
        boolean layeredRag,
        boolean toolGateway,
        boolean a2uiAppointment,
        boolean mcp) {
}
```

Add `@ConfigurationPropertiesScan` to `MedoraBackendApplication` and these defaults to `application.yml`:

```yaml
medora:
  evolution:
    context-builder: ${FEATURE_CONTEXT_BUILDER:false}
    layered-rag: ${FEATURE_LAYERED_RAG:false}
    tool-gateway: ${FEATURE_TOOL_GATEWAY:false}
    a2ui-appointment: ${FEATURE_A2UI_APPOINTMENT:false}
    mcp: ${FEATURE_MCP:false}
```

- [ ] **Step 4: Run the focused and config tests**

Run: `cd backend && mvn -Dtest=EvolutionPropertiesTest,AdkConfigLoadTest test`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/medora/common/config/EvolutionProperties.java backend/src/main/java/com/medora/MedoraBackendApplication.java backend/src/main/resources/application.yml backend/src/test/java/com/medora/common/config/EvolutionPropertiesTest.java
git commit -m "chore: add architecture evolution feature flags"
```

### Task 2: Freeze cross-module correlation and bounded metrics

**Files:**
- Modify: `backend/src/main/java/com/medora/common/metrics/MedoraMetricsService.java`
- Create: `backend/src/main/java/com/medora/common/metrics/EvolutionMetrics.java`
- Test: `backend/src/test/java/com/medora/common/metrics/EvolutionMetricsTest.java`

**Interfaces:**
- Consumes: Micrometer `MeterRegistry`.
- Produces: `recordContextBuild(profile,status)`, `recordRagStage(stage,status,duration)`, `recordToolCall(toolFamily,status,duration)`, and `recordAppointmentTransition(operation,status)`.

- [ ] **Step 1: Write a test that rejects high-cardinality labels**

```java
@Test void recordsOnlyBoundedTags() {
    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    EvolutionMetrics metrics = new EvolutionMetrics(registry);
    metrics.recordContextBuild("BOOKING", "SUCCESS");
    assertThat(registry.find("medora.context.build").tag("profile", "BOOKING").counter()).isNotNull();
    assertThat(registry.getMeters()).allSatisfy(m ->
            assertThat(m.getId().getTags()).noneMatch(t -> t.getKey().equals("userId") || t.getKey().equals("traceId")));
}
```

- [ ] **Step 2: Verify failure**

Run: `cd backend && mvn -Dtest=EvolutionMetricsTest test`

Expected: FAIL because `EvolutionMetrics` does not exist.

- [ ] **Step 3: Implement explicit enum-normalizing metrics methods**

Use fixed allow-lists inside `EvolutionMetrics`; map unknown values to `UNKNOWN`. Timers receive duration as `Duration`, while counters receive only bounded `profile`, `stage`, `operation`, and `status` tags. Delegate old appointment/tool counters from `MedoraMetricsService` during the transition instead of creating duplicate metric names.

- [ ] **Step 4: Run tests**

Run: `cd backend && mvn -Dtest=EvolutionMetricsTest,AppointmentCommandServiceTest,AdkEventToSseAdapterTest test`

Expected: PASS and no duplicate meter registration warnings.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/medora/common/metrics backend/src/test/java/com/medora/common/metrics
git commit -m "chore: add bounded evolution metrics"
```

### Task 3: Add deterministic baseline scenarios and runner

**Files:**
- Create: `backend/src/test/resources/eval/cases/evolution-baseline-v1.yaml`
- Create: `backend/src/test/resources/eval/scenarios/evolution-baseline-v1.yaml`
- Create: `scripts/run-a-stage-baseline.sh`
- Create: `docs/reports/.gitkeep`
- Modify: `backend/src/test/java/com/medora/eval/EvalCliIntegrationTest.java`
- Test: `backend/src/test/java/com/medora/eval/EvalCliIntegrationTest.java`

**Interfaces:**
- Consumes: existing `EvalCli`, Maven tests, and frontend build.
- Produces: `docs/reports/m0-agent-baseline.json` and a command that exits nonzero on any failure.

- [ ] **Step 1: Add baseline cases with stable assertions**

The YAML must include at least these IDs and expected route/tool behavior:

```yaml
---
id: baseline-triage-cough
category: ROUTING
userMessage: 我咳嗽一周了，应该挂哪个科？
context: { userId: 1 }
expectations:
  route: { expectedChain: [root_router_agent, triage_agent] }
  safety: ALLOW
metadata: { severity: normal, tags: [baseline, triage], source: SEED }
---
id: baseline-booking-query
category: BOOKING
userMessage: 查询明天上午的呼吸内科号源
context: { userId: 1 }
expectations:
  route: { expectedChain: [root_router_agent, booking_agent] }
  tools: [{ toolName: queryScheduleSlots, expectStatus: SUCCESS }]
  safety: ALLOW
metadata: { severity: normal, tags: [baseline, booking], source: SEED }
---
id: baseline-knowledge
category: KNOWLEDGE
userMessage: 挂号最晚什么时候可以取消？
context: { userId: 1 }
expectations:
  route: { expectedChain: [root_router_agent, knowledge_agent] }
  tools: [{ toolName: searchKnowledge, expectStatus: SUCCESS }]
  safety: ALLOW
metadata: { severity: normal, tags: [baseline, knowledge], source: SEED }
```

Create matching deterministic entries in `evolution-baseline-v1.yaml` under `scenarios:` with the exact route chains and successful required tool events.

- [ ] **Step 2: Extend the integration test to load every case**

Assert the file loads, IDs are unique, and each case has a nonblank input and expected route.

- [ ] **Step 3: Verify the focused test**

Run: `cd backend && mvn -Dtest=EvalCliIntegrationTest test`

Expected: PASS for the deterministic harness; no real API key required.

- [ ] **Step 4: Add the fail-fast baseline shell script**

```bash
#!/usr/bin/env bash
set -euo pipefail
(cd backend && mvn -Dtest='*Adk*,*Retrieval*,*Appointment*' test)
(cd backend && mvn test-compile exec:java -Dexec.mainClass=com.medora.eval.EvalCli -Dexec.classpathScope=test -Dexec.args="--cases src/test/resources/eval/cases/evolution-baseline-v1.yaml --scenarios src/test/resources/eval/scenarios/evolution-baseline-v1.yaml --report-dir ../docs/reports/m0-agent-baseline --threshold 1.0")
(cd frontend && npm run build)
```

The script must not echo environment contents. Make it executable with `chmod +x scripts/run-a-stage-baseline.sh`.

- [ ] **Step 5: Run the baseline**

Run: `./scripts/run-a-stage-baseline.sh`

Expected: exits 0; any Maven or Next.js failure stops the script.

- [ ] **Step 6: Commit**

```bash
git add backend/src/test/resources/eval/cases/evolution-baseline-v1.yaml backend/src/test/resources/eval/scenarios/evolution-baseline-v1.yaml backend/src/test/java/com/medora/eval/EvalCliIntegrationTest.java scripts/run-a-stage-baseline.sh docs/reports/.gitkeep
git commit -m "test: freeze a-stage behavior baseline"
```

## M0 Completion Gate

Run:

```bash
./scripts/run-a-stage-baseline.sh
git status --short
```

Accept when the baseline exits 0, feature flags are all off by default, metrics contain no high-cardinality tags, and no unrelated working-tree files were staged.
