# Medora Agent 医疗服务架构演进设计

**日期：** 2026-09-21  
**状态：** 已确认  
**交付策略：** 先完成 A（面试/作品集可演示），保留 B（小规模生产试点）升级路线

## 1. 背景与目标

Medora 当前已经具备 Spring Boot + Google ADK 多 Agent、PostgreSQL/pgvector 混合检索、Redis、MinIO、预约服务、会话摘要、长期记忆和本地工具抽象。项目不需要推倒重写，而应在现有模块化单体上补齐四条端到端闭环：

1. 多 Agent 动态上下文组装、摘要压缩、记忆筛选和 Token 预算；
2. 结构感知、语义边界和 Parent-Child 分层 RAG；
3. A2UI 预约确认、号源一致性、超时释放和候补补位；
4. 统一 Tool Gateway 以及最小可运行 MCP Server/Client。

本设计的首要目标是形成可运行、可测试、可解释、可展示的作品集系统。B 阶段的生产加固任务会保留，但不阻塞 A 阶段完成。

## 2. 当前系统差距

### 2.1 多 Agent 与上下文

当前已有 Root Router、导诊/预约/知识 Agent 和 ADK `transfer_to_agent`，但摘要、长期记忆、Redis 最近消息和 RAG 没有通过一个统一组件注入当前 ADK 主链路。`PostgresBackedSessionService` 固定恢复最近 30 条消息，旧的 Context Pruner/Token Budget 路径与当前 ADK 路径存在分离。

主要差距：

- 没有唯一 Context Builder；
- 没有按任务分配上下文预算；
- 长期记忆只做基础类型过滤和完全相同 key 的 upsert；
- 没有语义归一、冲突替换和跨摘要/历史/RAG 去重；
- 上下文选择原因和 Token 节省不可见。

### 2.2 RAG

当前已有 pgvector、PostgreSQL FTS、向量与关键词混合召回以及 `RerankService` 接口，但切分仍以段落和固定长度为主，`NoopReranker` 不执行真实重排，且候选结果在重排前已截断到最终 `topK`。

主要差距：

- 没有 Parent-Child 层级索引；
- 没有语义边界切分；
- 元数据过滤范围有限；
- 没有真实 Reranker；
- 缺少检索指标对照和索引诊断。

### 2.3 预约与库存

当前预约服务使用 PostgreSQL 悲观行锁和数据库幂等唯一索引，能够完成基本扣减和释放，但 Agent 获得 `slotId` 后可以直接调用写工具创建正式预约。

主要差距：

- 没有用户确认动作和一次性令牌；
- 没有临时保留、超时释放和完整状态机；
- Redis 尚未参与热点号源互斥；
- 没有补偿、对账和候补队列；
- 前端 SSE 没有动态 UI 协议；
- 缺少并发竞争和故障恢复测试。

### 2.4 Tool Gateway 与 MCP

当前已有 `ToolDefinition`、静态注册表、参数描述、风险等级和 ADK 适配器，是合适的演进接缝。

主要差距：

- 没有统一的发现、版本、输入输出 Schema 和执行管线；
- 没有 Agent/User/风险三层权限；
- 没有统一超时、限流、隔离、熔断和审计；
- 没有远程工具适配；
- 没有 MCP Client/Server。

## 3. 设计原则

1. **模块化单体优先。** A 阶段不拆微服务，不引入 Kafka 或 Kubernetes。
2. **PostgreSQL 是业务事实来源。** Redis 中的锁、缓存和 ZSet 必须可从数据库重建。
3. **高风险写操作需要用户确认。** Agent 只能创建预约提案或临时保留，不能直接正式预约。
4. **Context Builder 是唯一上下文入口。** 会话、摘要、记忆、业务状态和 RAG 不再各自拼接。
5. **沿用现有工具抽象。** Tool Gateway 包装现有 `ToolDefinition`，避免整体重写。
6. **先形成 A 闭环，再做 B 加固。** A 的接口和数据模型不得阻碍后续 Outbox、多实例和权限强化。
7. **所有能力可解释、可测试。** 每项优化都必须有诊断信息、自动化测试和前后对照指标。

## 4. 目标架构

```text
Next.js
├── 对话与引用
├── A2UI Surface Renderer
├── 预约/候补状态
└── 管理诊断页面
          │ REST + SSE
          ▼
Spring Boot 模块化单体
├── Agent Runtime
│   ├── Root Router + Specialist Agents
│   ├── Context Builder
│   ├── Summary / Memory
│   └── Token Budget
├── Knowledge / RAG
│   ├── Parent-Child Index
│   ├── Hybrid Retrieval
│   ├── Rerank
│   └── Citation
├── Appointment
│   ├── Confirmation Action
│   ├── State Machine
│   ├── Inventory Consistency
│   └── Waitlist Promotion
└── Tool Gateway
    ├── Registry / Schema / Policy
    ├── Local Tool Adapter
    └── MCP Adapter
          │
          ▼
PostgreSQL + pgvector  业务事实、索引、审计
Redis                  上下文缓存、锁、确认令牌、ZSet
MinIO                  原始知识文档
```

## 5. 多 Agent 与 Context Builder

### 5.1 执行链路

```text
用户消息
  → Context Profile 预判
  → Context Builder
  → ADK Root Router
  → transfer 到专业 Agent
  → Tool Gateway 或文本回复
  → 异步更新摘要、记忆候选和指标
```

Context Profile 只决定准备哪些上下文，不替代 ADK Root Router。最终路由仍由现有 ADK transfer 完成。

### 5.2 核心组件

#### ContextProfileResolver

输出 `TRIAGE`、`BOOKING`、`KNOWLEDGE` 或 `GENERAL`。优先使用确定性规则，无明确结果时使用 `GENERAL`，不为了预判额外调用一次 LLM。

#### ContextBuilder

统一调用各 Context Provider，输出结构化 `AgentContextBundle`：

- 当前消息和会话场景；
- 会话摘要；
- 相关近期消息；
- 筛选后的长期记忆；
- 按需 RAG 证据；
- 当前业务状态；
- 诊断和预算信息。

`AdkOrchestrator` 在 Runner 执行前构建 Bundle，并写入 ADK Session State。Agent instruction 只引用约定字段。

#### TokenBudgetAllocator

默认预算：

| 内容 | 默认占比 |
|---|---:|
| 系统指令与安全规则 | 20% |
| 当前消息与近期对话 | 25% |
| 会话摘要与业务状态 | 15% |
| RAG 证据 | 30% |
| 长期记忆 | 10% |

不同 Profile 可以覆盖比例。超预算时按以下优先级裁剪：

```text
安全规则 > 当前问题 > 业务状态 > RAG > 记忆 > 历史消息
```

A 阶段允许近似 Token 估算；B 阶段替换为真实 tokenizer。

#### ConversationCompressor

- 最近若干轮保留原文；
- 更早内容进入滚动结构化摘要；
- 摘要包含用户目标、已确认事实、未完成任务和最近工具结果；
- 摘要失败时确定性截断，不能阻塞主对话。

### 5.3 记忆筛选与去重

#### 结构化候选

记忆捕获统一产生 `MemoryCandidate`：

```text
memoryType
scopeKey
normalizedValue
summary
confidence
source
sourceReference
occurredAt
expiresAt
```

#### 标准化

- “上午”“早上”“午前”统一为 `AM`；
- 科室、医生优先对齐真实业务 ID；
- 识别否定、时间范围和显式替换表达；
- 医疗症状只作为短期、时效型事实，不能成为永久偏好。

#### 精确去重

```text
fingerprint = SHA-256(userId + memoryType + scopeKey + normalizedValue)
```

相同指纹不新建记录，而是增加 `evidenceCount`、更新确认时间和合并置信度。

#### 语义与实体去重

A 阶段使用有限业务词典、实体 ID 和文本归一，不为有限偏好类型额外建立向量库。自由文本相似度只能辅助合并，不能单独决定医疗事实。

#### 冲突处理

记忆类型标注单值、多值或时效型。单值记忆按来源强度处理：

```text
用户明确表达 > 成功业务操作 > 多次行为信号 > 单次提及 > LLM 推断
```

新明确偏好替代旧值时，旧记录进入 `INACTIVE` 并记录 `supersededBy`。偶然查询不能覆盖明确偏好；医疗事实冲突时优先询问用户。

#### 查询筛选与评分

硬过滤：

- 当前用户；
- `ACTIVE`；
- 未过期；
- 类型符合 Context Profile；
- 来源未撤销；
- 当前 Agent 有权访问。

排序公式：

```text
score = 任务相关度×0.40 + 置信度×0.25 + 来源质量×0.20 + 新鲜度×0.15
```

分组限制：

- 单值偏好每种最多 1 条；
- 最近成功预约最多 1 条；
- 历史型记忆每种最多 2 条；
- 最终结果受记忆 Token 预算约束。

#### 跨来源去重

记忆与当前消息、近期对话、会话摘要和 RAG 内容比较指纹。摘要中已有的事实不再以完整自然语言重复注入。

#### 可解释性

Context Diagnostics 记录候选数、过期过滤、意图过滤、冲突淘汰、重复淘汰、最终选入和估算 Token 节省。管理端仅显示脱敏内容。

### 5.4 A 阶段验收

- 超过 30 条消息仍能恢复早期关键事项；
- 同一偏好多种说法只保留一条；
- 明确新偏好能替代旧偏好；
- 单次查询不能覆盖明确偏好；
- 无关、过期、低置信度记忆不进入 Prompt；
- 摘要中已有事实不重复注入；
- Redis 或摘要服务不可用时对话可以降级完成；
- 管理端显示上下文来源、筛选原因和 Token 预算。

## 6. 分层 RAG

### 6.1 文档处理

```text
MinIO 原始文档
  → 结构解析
  → Parent Chunk
  → 语义边界切分
  → Child Chunks
  → Embedding + FTS + Metadata
  → PostgreSQL / pgvector
```

### 6.2 Parent-Child 模型

在 `knowledge_chunk` 上增加：

```text
chunk_type       PARENT / CHILD
parent_chunk_id  自关联父块
token_count
content_hash
page_number
```

- Parent 约 800～1200 Tokens，保存章节完整语义；
- Child 约 180～350 Tokens，用于检索；
- 相邻 Child 默认重叠约 40 Tokens；
- Parent 默认不参与向量召回；
- Child 命中后按需扩展 Parent 上下文；
- 引用仍精确定位 Child。

### 6.3 语义边界切分

1. 按标题、段落、FAQ、列表和表格划分自然单元；
2. 超长单元拆成句子；
3. 计算相邻句子或句组 Embedding 相似度；
4. 在相似度明显下降处优先切分；
5. 仍超长时按 Token 上限强制切分；
6. Embedding 不可用时降级到段落和句子规则。

`content_hash` 用于跳过未变化 Chunk 的重复向量化。

### 6.4 元数据

文档和 Chunk 支持：

- `documentType`；
- `departmentCode`；
- `hospitalCode`；
- `tags`；
- `effectiveFrom/effectiveTo`；
- `documentVersion`；
- `accessScope`；
- `status`。

权限、状态和有效期在召回前完成硬过滤。

### 6.5 检索链路

```text
查询标准化
  → 元数据/权限过滤
  → FTS Top 20 + pgvector Top 20
  → RRF 排名融合
  → 文档与内容指纹去重
  → 保留约 20 个候选
  → Reranker
  → Top 5 Child
  → Parent 上下文扩展
  → 引用组装
```

当前“先截断到最终 topK 再重排”的行为必须改为 `candidateK → rerank → finalTopK`。

### 6.6 Reranker

新增 `LlmReranker`，输出候选 ID、分数和理由，并经过 JSON Schema 校验。超时、异常或解析失败时回退到 RRF 排名。`NoopReranker` 只作为关闭和降级实现。

A 阶段使用已有 AI Chat API；B 阶段可替换独立 Cross-Encoder 或专业 rerank 服务。

### 6.7 引用与诊断

每条结果包含：

- 文档 ID、标题和版本；
- Parent/Child ID；
- 章节路径和页码；
- 命中片段；
- 关键词排名、向量排名、RRF 分和 rerank 分；
- 实际元数据过滤条件。

用户端显示标题、章节和片段；完整链路只在管理端诊断页展示。

### 6.8 评测

评测集覆盖 FAQ、中文同义表达、跨段落、无答案、过期文档、元数据过滤、多文档冲突、Parent 补全和混合召回优势案例。

核心指标：

- Recall@5；
- MRR@5；
- nDCG@5；
- 无答案拒答准确率；
- 引用命中率；
- 平均检索耗时；
- 平均 Prompt Token。

报告对比关键词、向量、混合检索+重排三组结果。

## 7. Tool Gateway

### 7.1 统一描述

每个工具注册为 `ToolDescriptor`：

```text
name
version
description
sourceType       LOCAL / MCP
inputSchema
outputSchema
riskLevel        LOW / MEDIUM / HIGH
requiredRoles
allowedAgents
timeout
idempotent
requiresConfirmation
dataClassification
```

工具名称命名空间化，如 `local.queryScheduleSlots`、`local.prepareAppointment`、`mcp.hospitalDirectory.findDepartment`。

### 7.2 执行管线

```text
Agent Tool Call
  → 解析描述
  → Agent 权限
  → 用户角色和数据权限
  → JSON Schema 输入校验
  → 确认/幂等策略
  → 限流、并发隔离、超时
  → Local/MCP Adapter
  → 输出 Schema 校验
  → 脱敏、审计、指标
  → 标准 ToolResult
```

标准错误类型：

```text
VALIDATION_ERROR
AUTHORIZATION_DENIED
CONFIRMATION_REQUIRED
BUSINESS_CONFLICT
DEPENDENCY_TIMEOUT
DEPENDENCY_UNAVAILABLE
RATE_LIMITED
INTERNAL_ERROR
```

### 7.3 权限与可靠性

- 知识 Agent 不能调用预约写工具；
- 普通用户不能调用管理员工具；
- 高风险工具必须返回 `CONFIRMATION_REQUIRED`；
- 查询型幂等工具允许有限重试；
- 写工具不由 Gateway 自动重试；
- 每个工具独立配置超时、最大并发、速率限制和熔断；
- 单个 MCP Server 故障不能影响其他工具和普通对话。

Tool Execution Context 扩展为用户、角色、Agent、会话、trace、request 和 confirmation action 信息。

### 7.4 MCP 接入

使用官方 Java SDK，并固定已测试的 SDK/BOM 和协议版本。优先采用 Streamable HTTP，不新建基于旧版 SSE 的实现。

#### Medora MCP Server

通过 `/mcp` 暴露只读工具：

- `searchMedicalKnowledge`；
- `queryScheduleSlots`。

不通过 MCP 开放正式预约写操作。服务使用 Token 鉴权、Origin 校验、权限校验、输出脱敏和 Schema 校验。

#### Medora MCP Client

增加独立演示容器 `hospital-directory-mcp`，暴露 `findDepartment`。Medora 启动后调用 `tools/list` 发现并注册为 `mcp.hospitalDirectory.findDepartment`，供导诊 Agent 通过 Gateway 调用。

MCP Server URL 来自配置白名单，Agent 不能指定任意 URL；服务凭证只从环境或 Secret 注入。

### 7.5 注册与健康状态

```text
REGISTERED
HEALTHY
DEGRADED
UNAVAILABLE
DISABLED
```

A 阶段支持启动发现、管理员刷新、周期健康检查、断线标记和恢复后重新发现。

## 8. A2UI 与预约一致性

### 8.1 协议范围

A 阶段固定 A2UI v0.9.1 受控子集。后端通过现有 SSE 增加 `a2ui` 事件，承载：

- `createSurface`；
- `updateComponents`；
- `updateDataModel`；
- `deleteSurface`。

前端只实现白名单组件：Text、Card、Column、Row、Button、Badge 和 Divider。禁止执行 Agent 生成的 HTML、JavaScript 或任意 React 代码。

参考：

- <https://github.com/a2ui-project/a2ui/blob/main/README.md>
- <https://github.com/a2ui-project/a2ui/blob/main/specification/v0_9_1/docs/a2ui_protocol.md>

### 8.2 交互边界

现有 Agent 侧 `bookAppointment` 替换为 `prepareAppointment`：

- 校验号源来自真实查询结果；
- 创建短期号源保留；
- 创建一次性确认令牌；
- 生成 A2UI 确认 Surface；
- 不创建正式预约。

用户操作调用：

```text
POST /api/a2ui/actions
```

请求只提交 `surfaceId`、`actionId`、`confirmationToken` 和 `idempotencyKey`。医生、日期、费用等关键参数由服务端快照读取。

### 8.3 状态机

```text
PENDING_CONFIRMATION
  ├── confirm → CONFIRMED
  ├── cancel  → CANCELLED
  └── timeout → EXPIRED

CONFIRMED
  └── cancel  → CANCELLED
```

B 阶段再扩展 COMPLETED、NO_SHOW、REFUNDING 和 REFUNDED。

状态变化只能通过领域方法完成。

### 8.4 库存不变量

```text
availableQuota >= 0
reservedQuota >= 0
availableQuota + reservedQuota + confirmedQuota = totalQuota
```

| 操作 | available | reserved | confirmed |
|---|---:|---:|---:|
| 创建临时保留 | -1 | +1 | 0 |
| 确认预约 | 0 | -1 | +1 |
| 保留超时/取消 | +1 | -1 | 0 |
| 已确认退号 | +1 | 0 | -1 |

`confirmedQuota` 可以计算得出，不强制冗余存储。

号源状态独立维护 OPEN、FULL 和 CLOSED。

### 8.5 并发控制

```text
幂等检查
  → Redis 号源锁
  → PostgreSQL SELECT ... FOR UPDATE
  → 再次检查状态和库存
  → 更新库存、预约和日志
  → 提交事务
  → 释放 Redis 锁
```

Redis 用于减少热点竞争，PostgreSQL 行锁、条件更新和唯一约束是最终保障。Redis 不可用时允许降级到数据库锁。所有流程采用一致锁顺序，锁使用唯一 owner token。

### 8.6 幂等与确认令牌

令牌随机生成，数据库保存摘要，绑定用户、号源、预约和 Action，并设置过期时间。消费成功后标记 `CONSUMED`；重复确认返回首次结果。

数据库唯一约束至少覆盖：

```text
(user_id, idempotency_key)
(action_id)
(confirmation_token_hash)
```

### 8.7 超时释放与补偿

- Redis 过期集合快速发现到期保留；
- PostgreSQL 定时扫描作为最终兜底；
- 只有成功从 PENDING_CONFIRMATION 转到 EXPIRED 的线程可以返还库存；
- 库存与预约状态在同一数据库事务中更新；
- A 阶段使用 Spring 事务后事件和定时对账；
- B 阶段升级 Transactional Outbox。

### 8.8 候补队列

数据库状态：

```text
WAITING → OFFERED → CONFIRMED
   │          └── timeout → EXPIRED
   └── cancel → CANCELLED
```

Redis ZSet：

```text
key: waitlist:{slotId}
member: waitlistEntryId
score: priority + joinedAt
```

A 阶段普通用户同优先级，按加入时间 FIFO。退号或保留超时发布 `SlotReleased`，处理器锁定号源、`ZPOPMIN`、回查数据库并为首位有效候补创建限时 OFFERED。候补用户仍需确认，系统不能直接代替用户正式预约。

Redis 数据丢失后从数据库 WAITING 记录重建。

### 8.9 A 阶段验收

- Agent 不能直接创建正式预约；
- 伪造 slotId 不能通过来源校验；
- 确认令牌只能由所属用户使用一次；
- 100 个并发请求竞争 10 个号源时占用不超过 10；
- 相同幂等键只产生一条有效记录；
- 超时库存准确恢复；
- Redis 不可用时仍不超卖；
- 退号后第一位有效候补获得限时确认资格；
- 候补超时后轮到下一位；
- Redis 清空后可重建队列；
- 非白名单 A2UI 组件和非法 Action 被拒绝；
- 所有状态变化有操作日志和 traceId。

## 9. 工程保障

### 9.1 数据迁移

- 只新增 Flyway Migration，不修改已执行迁移；
- 先新增可空字段和表，再回填，最后添加约束；
- Parent-Child 上线期间兼容旧 Chunk；
- 预约状态机上线前迁移旧状态；
- 每次迁移包含数据校验 SQL；
- Feature Flag 控制新旧链路，不直接删除旧数据。

### 9.2 测试分层

```text
单元测试
├── 状态机
├── 记忆去重与冲突
├── Token 预算
├── Chunker
└── Tool Policy

Testcontainers 集成测试
├── PostgreSQL / pgvector
├── Redis
├── 幂等和数据库约束
├── RAG 查询
└── 超时释放和候补重建

协议/契约测试
├── SSE
├── A2UI Schema
├── Tool JSON Schema
└── MCP Streamable HTTP

并发测试
├── 同号源竞争
├── 重复确认
├── 超时与确认竞争
└── 退号与候补竞争

端到端测试
└── 对话 → A2UI → 确认 → 预约结果
```

外部模型在 CI 使用 Stub 或录制回放；真实模型评测由独立命令执行。

### 9.3 可观测性

统一关联字段：

```text
traceId
conversationId
agentRunId
toolCallId
surfaceId
actionId
appointmentId
```

核心指标：

- Agent 路由、transfer 深度和失败率；
- 上下文来源 Token 数和裁剪数；
- RAG 各阶段耗时、Recall 和重排降级；
- 工具调用成功率、P95 和熔断次数；
- 锁等待、预约冲突、超时释放和库存差异；
- 候补长度、补位成功率和确认超时率。

A 阶段使用 Spring Actuator、Micrometer 和管理端诊断；B 阶段接 Prometheus、Grafana 和告警。

## 10. A 阶段开发里程碑

### M0 基线与清理

- 固定 Agent、RAG、预约现有行为测试；
- 标记旧上下文和 Agent 遗留路径；
- 建立统一 Trace、指标和 Feature Flag；
- 保存改造前 RAG、Token 和预约吞吐基线。

### M1 Context Builder 与记忆治理

- 完成 Context Bundle、Profile、预算、压缩、记忆标准化/去重/冲突/筛选和诊断；
- 接入 ADK Session State；
- 完成长会话和降级测试。

### M2 分层 RAG

- 完成 Parent-Child、语义切分、元数据过滤、RRF、Reranker、引用和评测报告。

### M3 Tool Gateway 核心

- 完成统一描述、Schema、权限、确认、故障隔离、审计和本地工具迁移。

### M4 A2UI 与预约库存

- 完成 A2UI Renderer、Action API、确认令牌、状态机、临时保留、锁、超时释放、候补和并发测试。

### M5 MCP 接入

- 完成 Medora MCP Server、演示 MCP Server、Client Adapter、发现、健康状态和真实 Streamable HTTP 测试。

### M6 作品集交付

- 一键 Docker Compose；
- 初始化知识和预约数据；
- 三条主演示路线；
- 架构/时序图；
- Token、RAG 和并发报告；
- README 和故障演练说明。

三条主演示路线：

1. 长会话导诊：路由、摘要、记忆筛选和 Token 预算；
2. 知识问答：Parent-Child、混合召回、重排和引用；
3. 预约候补：A2UI 确认、并发库存、超时释放和自动补位。

## 11. B 阶段可选路线

B 不阻塞 A 的完成定义，按优先级保留：

### B1 可靠性

- Transactional Outbox；
- 多实例任务抢占；
- 事件重放；
- 数据备份和恢复演练。

### B2 安全性

- OAuth 2.1 和细粒度 Scope；
- 敏感数据加密；
- MCP 凭证托管与轮换；
- 更完整审计策略。

### B3 可观测性

- Prometheus；
- Grafana；
- 告警；
- 容量和成本趋势。

### B4 检索运营

- 蓝绿索引；
- 增量重建；
- 断点恢复；
- 线上低质量样本回流；
- 独立 rerank 服务。

### B5 部署

- 2～3 后端实例；
- 反向代理；
- 灰度和回滚；
- 多实例健康检查。

### B6 容量

- 更高并发压测；
- 热点号源削峰；
- 数据库与 Redis 调优；
- 库存对账和自动告警。

## 12. 非目标

A 阶段明确不包含：

- 微服务拆分；
- Kafka 集群；
- Kubernetes 平台；
- 公共 MCP 工具市场；
- 医院正式合规认证；
- 复杂医疗优先级候补规则；
- 自动医疗诊断；
- 无人工确认的高风险业务写入。

## 13. 成功标准

A 阶段只有同时满足以下条件才视为完成：

1. 四条核心架构能力均有真实端到端运行链路；
2. 高风险预约必须经过 A2UI 确认；
3. 并发预约不超卖，重复提交不重复创建；
4. 记忆筛选、Token 优化和 RAG 改进有可量化对照；
5. MCP 使用真实协议完成发现和调用；
6. 关键降级路径有自动化测试；
7. 新环境可按 README 一键启动并复现三条主演示；
8. B 阶段升级点已在接口、数据模型和文档中保留，但不影响 A 验收。
