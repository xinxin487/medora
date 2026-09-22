# A-stage architecture overview

This map uses the concrete classes, routes, and storage boundaries implemented in the A-stage branch.

```mermaid
flowchart TB
    UI[Next.js\n/app/assistant · /admin/context · /admin/rag · /admin/tools]
    CHAT[POST /api/v1/chat/conversations/{conversationId}/stream\nChatStreamController]
    A2UI[POST /api/v1/a2ui/actions\nA2uiActionController]
    ADMIN[Admin diagnostics APIs]

    RUNTIME[ChatRuntimeService → ChatOrchestrator → AdkOrchestrator]
    AGENTS[AdkAgentTopology\nRoot Router → Triage / Booking / Knowledge]
    CONTEXT[ContextBuilder\nTokenBudgetAllocator · MemorySelector]
    GATEWAY[ToolGateway\npolicy · schema · isolation · audit]
    RAG[RetrievalService\nHybridRetriever · RRF · RerankService · ParentContextExpander]
    BOOKING[AppointmentHoldService · A2uiActionService\nAppointmentCommandService · WaitlistPromotionHandler]
    MCPCLIENT[McpDiscoveryService · McpToolAdapter]
    MCPSERVER[MedoraMcpServerConfig\n/mcp]
    DIRECTORY[hospital-directory-mcp\n/mcp]

    PG[(PostgreSQL + pgvector\nsource of truth)]
    REDIS[(Redis\nrebuildable locks/index/cache)]
    MINIO[(MinIO\noriginal knowledge files)]

    UI --> CHAT --> RUNTIME
    UI --> A2UI --> BOOKING
    UI --> ADMIN
    RUNTIME --> CONTEXT
    RUNTIME --> AGENTS
    AGENTS --> GATEWAY
    GATEWAY --> RAG
    GATEWAY --> BOOKING
    GATEWAY --> MCPCLIENT --> DIRECTORY
    MCPSERVER --> GATEWAY

    CONTEXT --> PG
    CONTEXT -. cache failure degrades .-> REDIS
    RAG --> PG
    RAG --> MINIO
    BOOKING == transactional truth ==> PG
    BOOKING -. lock / expiry ZSet / waitlist ZSet .-> REDIS
    ADMIN --> PG
```

PostgreSQL owns appointments, holds, confirmation actions, waitlist status, knowledge metadata, chunks, memories, and audit records. Redis only accelerates locks, expiry discovery, waitlist ordering, and recent context; its state is rebuildable from PostgreSQL.

MCP has two directions: authenticated read-only Medora tools are exposed at backend `/mcp`, while `hospital-directory-mcp` is an independent Streamable HTTP service discovered and registered through `McpDiscoveryService` and `McpToolAdapter`.
