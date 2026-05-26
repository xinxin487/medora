# RAG 系统差距分析与优化计划

## 0. 分析范围

对比对象：
- [design/03-api-contract-design.md](design/03-api-contract-design.md) — API 契约设计
- [design/04-agent-rag-memory-design.md](design/04-agent-rag-memory-design.md) — Agent/RAG/Memory 详细设计
- 当前代码实现 (feature/p8 分支)

分析范围：文档上传 → 解析 → 清洗 → 切分 → 向量化 → 存储 → 检索 → 重排 → Prompt 组装全链路。

---

## 1. 总体评估

当前 RAG 系统核心链路完整可用，实现了：

| 阶段 | 状态 | 说明 |
|------|------|------|
| 文档上传 API | ✅ 完整 | 6 个 API 均对齐设计，含上传/列表/详情/重建/发布/检索测试 |
| 文档解析 | ⚠️ 基础 | 仅支持 .md/.txt，无 PDF/Office |
| 文本清洗 | ⚠️ 内联 | 清洗逻辑在 `DocumentChunker.clean()` 中，无独立模块 |
| 文档切分 | ✅ 完整 | 标题感知 + 段落优先 + 700字窗口 + 120字 overlap |
| 向量化 | ✅ 完整 | 双模式：local-hash（开发）/ openai-compatible（生产） |
| 文档存储 | ❌ 缺失 | 原始文件未持久化到 MinIO，仅存内存解析文本 |
| 关键词检索 | ✅ 完整 | PostgreSQL FTS (content_tsv) + LIKE 组合评分 |
| 向量检索 | ✅ 完整 | pgvector 余弦距离 (<=>) |
| 混合融合 | ✅ 完整 | 0.55 关键词 + 0.45 向量加权融合，阈值 0.15 |
| 重排 | ❌ 缺失 | 无 RerankService，设计预留但未实现 |
| 长期记忆 | ✅ 完整 | 4 种记忆类型，按意图过滤读取 |
| 会话摘要 | ✅ 完整 | 12 条消息触发，业务状态追踪 |
| 异步处理 | ❌ 缺失 | 摄入全程同步阻塞 HTTP 线程 |

---

## 2. 差距详表

### 2.1 文档接入与解析

| # | 差距 | 设计依据 | 现状 | 影响 |
|---|------|----------|------|------|
| G1 | **原始文件未持久化** | 04-design §6.2: "文档写入 MinIO" | `DocumentParser` 直接从 `MultipartFile.getBytes()` 解析，不存 MinIO。上传后原始文件丢失，无法追溯/重建 | 重建需重新上传；无法做版本对比 |
| G2 | **无 PDF/Office 解析** | 03-design §10.1: 通用文档上传 | 仅支持 `.md` 和 `.txt`，其余返回 `MANUAL` 类型 | 医疗场景的 PDF 政策文件、Word 说明文档无法直接使用 |
| G3 | **无独立 DocumentCleaner** | 04-design §6.1: 模块列表含 `DocumentCleaner` | 清洗逻辑在 `DocumentChunker.clean()` 中内联 | 无法单独测试清洗逻辑，扩展清洗规则困难 |

### 2.2 文档摄入与处理

| # | 差距 | 设计依据 | 现状 | 影响 |
|---|------|----------|------|------|
| G4 | **摄入为同步阻塞** | 04-design §6.2: 文档接入流程含异步任务 | `DocumentIngestService.ingest()` 在 HTTP 请求线程中同步执行，逐个 chunk 生成 embedding 并执行 updateEmbedding SQL | 大文档上传可能导致 HTTP 超时（每个 chunk 一次 HTTP 调用 + 一次 SQL） |
| G5 | **无 TokenBudgetPlanner** | 04-design §10: 上下文优先级与裁剪 | `ContextPruner` 存在但无 token 预算计算器 | Prompt 裁剪无量化依据，依赖硬编码阈值 |
| G6 | **无 FAQ 单元切分策略** | 04-design §6.3: "FAQ 文档按问答单元切" | 所有文档统一按段落+标题切分，无 FAQ 检测 | FAQ 类文档可能切碎问题-答案对 |
| G7 | **切分参数不可配置** | 04-design §6.3: 参数可调 | `TARGET_SIZE=700`, `OVERLAP_SIZE=120` 硬编码 | 不同文档类型无法自适应 |

### 2.3 检索与重排

| # | 差距 | 设计依据 | 现状 | 影响 |
|---|------|----------|------|------|
| G8 | **无 RerankService** | 04-design §6.1: "RerankService — 否，但预留" + §6.11 详细接口设计 | 混合检索后直接返回结果，无重排阶段 | 候选结果多时无法提升最相关片段排序 |
| G9 | **无 RetrievalResultAssembler** | 04-design §6.1: 模块列表含 `RetrievalResultAssembler` | 结果组装在 `RetrievalService.toResult()` 内联 | 无法定制不同场景的组装策略 |
| G10 | **检索结果缺少 sectionPath** | 04-design §6.12: 结果应含 sectionPath + whySelected | `RetrievalResult` 包含 sectionPath 但不传至 Prompt 上下文（PromptBuilder 未使用） | 模型无法感知知识层级 |
| G11 | **关键词检索无中文分词** | 04-design §6.5: FTS 检索 | 使用 `plainto_tsquery('simple', query)`，无中文分词器 | "呼吸内科"作为整体匹配，无法应对"呼吸科"→"呼吸内科"的模糊匹配 |

### 2.4 记忆系统

| # | 差距 | 设计依据 | 现状 | 影响 |
|---|------|----------|------|------|
| G12 | **记忆写入触发单一** | 04-design §8.5: "用户显式确认偏好、多轮重复表述" | 仅 `BOOK_APPOINTMENT` 成功触发 `captureFromAgentRun()` | 用户的科室偏好、时段偏好只在预约成功后记录，对话中的明确偏好未记录 |
| G13 | **无置信度评分** | 04-design §8.5: "对候选项打置信分" | `UserMemoryProfileEntity.confidence` 字段存在但始终为 `null` | 无置信度升降机制，低质量记忆无法降权 |
| G14 | **无遗忘/过期机制** | 04-design §8.7: "低置信长期不用的记录降权，达到过期时间后归档" | `UserMemoryProfileEntity.expiresAt` 字段存在但从不设置 | 记忆只增不减，过期偏好可能干扰当前决策 |

### 2.5 测试覆盖

| # | 差距 | 设计依据 | 现状 | 影响 |
|---|------|----------|------|------|
| G15 | **测试覆盖不足** | 合理工程实践 | `DocumentChunkerTest` 1 测试、`MemoryServiceTest` 1 测试、`ConversationSummaryServiceTest` 1 测试、`RetrievalServiceTest` 3 测试 | 重构时无安全网 |

---

## 3. 与设计文档的对齐结论

### 03-api-contract-design.md 对齐情况

| 接口 | 状态 | 说明 |
|------|------|------|
| `POST /admin/knowledge/documents/upload` | ✅ 对齐 | 字段与响应结构一致 |
| `GET /admin/knowledge/documents` | ✅ 对齐 | 分页 + 过滤参数一致 |
| `GET /admin/knowledge/documents/{documentId}` | ✅ 对齐 | 返回结构一致 |
| `POST /admin/knowledge/documents/{documentId}/rebuild` | ✅ 对齐 | 请求参数一致 |
| `POST /admin/knowledge/documents/{documentId}/publish` | ✅ 对齐 | publishAction 一致 |
| `POST /admin/knowledge/search/test` | ✅ 对齐 | 返回结构一致 |

### 04-agent-rag-memory-design.md 对齐情况

| 模块 | 状态 | 说明 |
|------|------|------|
| ChatOrchestrator | ✅ | 完整实现 |
| IntentRouter | ✅ | 完整实现 |
| SafetyGuard | ✅ | Phase 9 增强完成 |
| MemoryService | ⚠️ | 基础读写完整，缺置信度/遗忘 |
| ConversationSummaryService | ✅ | 完整实现 |
| RetrievalService | ✅ | 完整实现 |
| HybridRetriever | ✅ | 完整实现 |
| KeywordRetriever | ✅ | 完整实现 |
| VectorRetriever | ✅ | 完整实现 |
| RerankService | ❌ | 未实现 |
| RetrievalResultAssembler | ❌ | 未实现 |
| DocumentCleaner | ❌ | 未实现 |
| TokenBudgetPlanner | ❌ | 未实现 |
| DocumentPersister (MinIO) | ❌ | 未实现 |
| ContextPruner | ✅ | 完整实现 |
| StreamEventPublisher | ✅ | 完整实现 |
| AgentTraceService | ✅ | Phase 9 增强完成 |

---

## 4. 优化计划

### Phase RAG-1: 基础加固与完善（P0）

#### G1: 原始文件持久化到 MinIO

**改动量**: 中（2个文件修改 + 1个新文件）

```
backend/src/main/java/com/medora/knowledge/infra/KnowledgeFileStore.java  ← 新建
backend/src/main/java/com/medora/knowledge/service/KnowledgeDocumentService.java  ← 修改
backend/src/main/java/com/medora/knowledge/parser/DocumentParser.java  ← 修改
```

- 新建 `KnowledgeFileStore`：封装 MinIO 客户端，提供 `store(documentId, MultipartFile)` / `load(documentId)` / `delete(documentId)`
- 上传流程改为：`KnowledgeFileStore.store()` → `DocumentParser.parse()`（从 MinIO 读取字节）
- `KnowledgeDocumentEntity` 增加 `filePath` 字段记录 MinIO 路径
- 需新增 migration V14 加列
- 重建流程从 MinIO 读取原始文件重新解析

#### G4: 异步摄入

**改动量**: 中（2 个文件修改）

```
backend/src/main/java/com/medora/knowledge/service/KnowledgeDocumentService.java  ← 修改
backend/src/main/java/com/medora/knowledge/service/DocumentIngestService.java  ← 修改
```

- `DocumentIngestService.ingest()` 改为 `@Async` 异步执行
- 上传接口立即返回 `DRAFT` 状态 + `ingestStatus=PROCESSING`
- 前端轮询或通过 SSE 通知摄入完成
- 也可选 Spring `ApplicationEventPublisher` 触发异步任务

#### G2: PDF/Office 解析支持

**改动量**: 大（引入新依赖 + 新解析器）

```
backend/pom.xml  ← 新增依赖
backend/src/main/java/com/medora/knowledge/parser/  ← 扩展
```

- 引入 Apache Tika（统一文档解析库，支持 PDF、Docx、xlsx 等）
- `DocumentParser` 增加 Tika 解析分支
- 或轻量方案：引入 PDFBox（仅 PDF）+ POI（仅 Word）

#### G8: RerankService 占位实现

**改动量**: 小（2 个新文件）

```
backend/src/main/java/com/medora/knowledge/retriever/RerankService.java  ← 新建
backend/src/main/java/com/medora/knowledge/retriever/CrossEncoderReranker.java  ← 新建（占位）
```

- 定义 `RerankService` 接口 + `NoopReranker` 默认实现（直接返回原列表）
- `HybridRetriever` 增加 `RerankService` 依赖
- 一期默认 noop，二期可替换为 cross-encoder 模型
- 遵循设计文档 "预留标准接口" 的要求

### Phase RAG-2: 检索与记忆增强（P1）

#### G12: 记忆写入多触发

**改动量**: 中（2 个文件修改）

```
backend/src/main/java/com/medora/memory/service/MemoryService.java  ← 修改
backend/src/main/java/com/medora/agent/application/ChatOrchestrator.java  ← 修改
```

- 增加触发场景：
  - 用户显式确认偏好（"我习惯挂上午的号"）
  - 多轮对话中重复出现相同科室/医生（>2 次）
  - 工具查询结果中用户确认选择
- `MemoryService.extractCandidates()` 从用户消息和工具结果中抽取候选

#### G13: 置信度评分机制

**改动量**: 小（1 个文件修改）

```
backend/src/main/java/com/medora/memory/service/MemoryService.java  ← 修改
```

- 实现置信度评分规则：
  - 预约成功写入 → confidence=1.0
  - 用户显式确认 → confidence=0.8
  - 多轮重复出现 → confidence+=0.1/轮，上限 0.9
  - 单次提及 → confidence=0.3
- 读取时按 `confidence DESC` 排序，低于阈值的不注入 Prompt

#### G14: 记忆过期机制

**改动量**: 小（1 个文件修改）

```
backend/src/main/java/com/medora/memory/service/MemoryService.java  ← 修改
```

- 写入时设置 `expiresAt`
- 读取时过滤 `expiresAt > now()` 或 `expiresAt IS NULL`
- 默认有效期为 180 天
- 可通过 scheduled task 清理过期记忆

#### G5: TokenBudgetPlanner

**改动量**: 中（1 个新文件 + 1 个修改）

```
backend/src/main/java/com/medora/agent/support/TokenBudgetPlanner.java  ← 新建
backend/src/main/java/com/medora/agent/application/PromptBuilder.java  ← 修改
```

- 根据模型估算 token 预算（gpt-4.1-mini ≈ 上下文窗口）
- 按上下文优先级（P0-P5）分配 token 预算
- `PromptBuilder` 调用 `TokenBudgetPlanner` 决定裁剪策略

### Phase RAG-3: 工程优化与测试（P2）

#### G9: RetrievalResultAssembler

**改动量**: 小（1 个新文件 + 1 个修改）

```
backend/src/main/java/com/medora/knowledge/retriever/RetrievalResultAssembler.java  ← 新建
backend/src/main/java/com/medora/knowledge/service/RetrievalService.java  ← 修改
```

- 从 `RetrievalService.toResult()` 提取为独立组件
- 支持不同场景的组装策略（管理后台搜索测试 / Agent 检索 / 纯知识问答）

#### G6: FAQ 单元切分

**改动量**: 小（1 个文件修改）

```
backend/src/main/java/com/medora/knowledge/chunk/DocumentChunker.java  ← 修改
```

- FAQ 检测：识别 "Q:" / "A:" / "问：" / "答：" / "？" 等模式
- FAQ 文档按问答对整体保留，不做段落切分

#### G7: 切分参数可配置

**改动量**: 小（1 个文件修改）

```
backend/src/main/java/com/medora/knowledge/chunk/DocumentChunker.java  ← 修改
```

- 将 `TARGET_SIZE` 和 `OVERLAP_SIZE` 改为构造参数注入
- 通过 `AiProperties` 配置或 `KnowledgeDocument.bizScope` 感知

#### G11: 中文分词增强

**改动量**: 小（引入依赖 + 修改检索器）

```
backend/pom.xml  ← 新增依赖
backend/src/main/java/com/medora/knowledge/retriever/KeywordRetriever.java  ← 修改
```

- 引入 `jieba` 或 `HanLP` 中文分词库
- 查询时对用户输入分词，构建更精确的 FTS 查询
- 或直接启用 PostgreSQL 中文分词配置

#### G15: 测试覆盖增强

**改动量**: 中（5+ 个测试文件）

```
backend/src/test/java/com/medora/knowledge/chunk/DocumentChunkerTest.java  ← 扩展
backend/src/test/java/com/medora/knowledge/service/RetrievalServiceTest.java  ← 扩展
backend/src/test/java/com/medora/memory/service/MemoryServiceTest.java  ← 扩展
backend/src/test/java/com/medora/memory/service/ConversationSummaryServiceTest.java  ← 扩展
（新增）
backend/src/test/java/com/medora/knowledge/parser/DocumentParserTest.java  ← 新建
backend/src/test/java/com/medora/ai/embedding/EmbeddingServiceTest.java  ← 新建（可选）
```

---

## 5. 优先级与工作量估算

| 优先级 | 差距编号 | 描述 | 工作量 | 影响 |
|--------|----------|------|--------|------|
| P0 | G1 | 原始文件持久化到 MinIO | 半天 | 数据持久性 |
| P0 | G4 | 异步摄入 | 半天 | HTTP 阻塞风险 |
| P0 | G2 | PDF/Office 解析 | 1天 | 文档格式支持 |
| P0 | G8 | RerankService 占位接口 | 0.5天 | 架构预留 |
| P1 | G12 | 记忆写入多触发 | 0.5天 | 记忆覆盖率 |
| P1 | G13 | 置信度评分 | 0.5天 | 记忆质量 |
| P1 | G14 | 记忆过期机制 | 0.5天 | 记忆污染 |
| P1 | G5 | TokenBudgetPlanner | 1天 | Prompt 质量控制 |
| P2 | G6 | FAQ 单元切分 | 0.5天 | 切分质量 |
| P2 | G7 | 切分参数可配置 | 0.5天 | 灵活性 |
| P2 | G9 | RetrievalResultAssembler | 0.5天 | 代码组织 |
| P2 | G11 | 中文分词增强 | 1天 | 检索精度 |
| P2 | G15 | 测试覆盖增强 | 1天 | 质量保障 |

---

## 6. 实施顺序建议

### Phase RAG-1: 基础加固（~3天）
```
Week 1: G1 (MinIO) + G4 (异步) → 解决数据持久性和 HTTP 阻塞风险
Week 1: G2 (PDF/Office) → 扩展文档格式支持
Week 2: G8 (RerankService) → 预留重排序接口
```

### Phase RAG-2: 检索与记忆增强（~3天）
```
Week 2: G12 (多触发) + G13 (置信度) + G14 (遗忘) → 记忆系统完善
Week 3: G5 (TokenBudgetPlanner) → Prompt 预算控制
```

### Phase RAG-3: 工程优化（~2.5天）
```
Week 3: G6 (FAQ) + G7 (可配置) + G9 (Assembler) → 工程完善
Week 3: G11 (中文分词) + G15 (测试覆盖) → 质量提升
```

---

## 7. 不纳入计划的项目

以下为设计文档提及但经评估不适合当前阶段的项：

| 项 | 设计来源 | 不纳入原因 |
|----|----------|-----------|
| 完整对话回放 (G-TRACE-5) | Phase 9 文档 | 需要在 agent_trace 存完整 prompt/response，存储成本高，价值有限 |
| 知识图谱增强 | 04-design §15.2 | 一期明确排除 |
| 多 Agent 路由 | 04-design §15.2 | 一期明确排除 |
| 独立检索 worker | 04-design §15.2 | 一期明确排除，异步摄入已覆盖主要痛点 |
| 流式输出预校验 | Phase 9 文档 | 需要在输出前阻塞等待校验完成，与流式体验冲突 |

---

## 8. 结论

当前 RAG 系统在核心链路上完整可用（上传→切分→向量化→混合检索→Prompt 注入），与 03-api-contract-design.md 的 API 契约高度对齐。与 04-agent-rag-memory-design.md 的差距主要集中在三个方向：

1. **数据持久性**（G1 缺 MinIO、G4 同步阻塞）— 是最大风险点
2. **检索质量**（G8 缺 Rerank、G11 缺中文分词）— 影响用户体验
3. **记忆系统**（G12/G13/G14 置信度与遗忘）— 影响长期使用质量

建议优先解决 P0 项（MinIO + 异步 + PDF/Office + Rerank 占位），再逐步完善记忆系统和工程优化。
