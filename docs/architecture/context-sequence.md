# Context Builder sequence

`FEATURE_CONTEXT_BUILDER=true` activates this path before every ADK run.

```mermaid
sequenceDiagram
    actor User
    participant UI as /app/assistant
    participant API as ChatStreamController
    participant Runtime as ChatRuntimeService
    participant ADK as AdkOrchestrator
    participant Builder as ContextBuilder
    participant Budget as TokenBudgetAllocator
    participant Memory as MemorySelector
    participant Session as PostgresBackedSessionService
    participant Agents as AdkAgentTopology
    participant Trace as AgentTraceService

    User->>UI: Send current turn
    UI->>API: POST /api/v1/chat/conversations/{id}/stream
    API->>Runtime: stream(...)
    Runtime->>ADK: orchestrate(AgentChatCommand)
    ADK->>Builder: build(ContextBuildRequest, maxTokens=8000)
    Builder->>Budget: allocate(profile, 8000)
    Builder->>Memory: select(candidates, budget, fingerprints)
    Memory-->>Builder: selected items + reason codes
    Builder-->>ADK: AgentContextBundle
    ADK->>Session: createSession(state: profile, memories, RAG, business, diagnostics)
    ADK->>Agents: Runner.runAsync(...)
    Agents-->>Runtime: SSE deltas / tool status / citations / A2UI
    Runtime->>Trace: record trace + sanitized context diagnostics
    Runtime-->>UI: message_end
    User->>UI: Open /admin/context with traceId
    UI->>Trace: GET /api/v1/admin/context-diagnostics/{traceId}
```

The diagnostic endpoint returns profile, budget, source/reason counts, fingerprints, and degradation state. It deliberately does not return raw memory or conversation text.
