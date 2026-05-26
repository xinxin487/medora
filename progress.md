# Progress Log

## Session: 2026-05-26

### Phase 0: 差距分析完成（前置工作）
- **Status:** complete
- **Started:** 2026-05-26
- Actions taken:
  - 读取两份设计文档 (03-api-contract-design.md / 04-agent-rag-memory-design.md)
  - 全量探索 RAG 实现代码（DocumentParser / DocumentChunker / DocumentIngestService / EmbeddingService / RetrievalService / HybridRetriever / KeywordRetriever / VectorRetriever / MemoryService / ConversationSummaryService）
  - 对比设计与实现，识别 15 项差距
  - 输出 docs/RAG系统差距分析与优化计划.md
- Files created/modified:
  - docs/RAG系统差距分析与优化计划.md (created)
  - task_plan.md (created)
  - findings.md (created)
  - progress.md (created)

### Phase RAG-1: 基础加固（已完成）
- **Status:** complete
- **Started:** 2026-05-26
- **Completed:** 2026-05-26
- Actions taken:
  - G1: MinIO 持久化 — 新增 MinioProperties/MinioConfig/KnowledgeFileStore，V14 迁移
  - G2: PDF/Office 解析 — 引入 Apache Tika，DocumentParser 新增 PDF/DOCX/XLSX 支持
  - G4: 异步摄入 — @EnableAsync + AsyncConfig，@Async + TransactionTemplate
  - G8: RerankService 占位 — 接口 + NoopReranker，注入 HybridRetriever
- Files created/modified:
  - backend/pom.xml, *.java (14 files), V14 migration, application.yml
- **Test results:** 31/31 tests passed, BUILD SUCCESS

### Phase RAG-2: 检索与记忆增强（已完成）
- **Status:** complete
- **Started:** 2026-05-26
- **Completed:** 2026-05-26
- Actions taken:
  - G12: `captureFromUserMessage()` — 时段偏好从 4 模式扩展到 20+ 模式；新增科室/医生提及检测（正则）；新增 `captureScheduleQuerySignal()` 处理号源查询结果
  - G13: `computeConfidence()` 评分规则引擎（BOOK_APPOINTMENT=1.0, USER_EXPLICIT=0.8, USER_MENTION=0.3, TOOL_QUERY=0.3）；`upsertMemory()` 置信度合并取 max；同源多轮重复 +0.1 递增至上限 0.9
  - G14: 默认 180 天 TTL；`loadRelevantMemory()` 过期过滤；`@Scheduled` 每日 3 点清理；`expire()` 方法；`findByExpiresAtBeforeAndStatus` 仓库方法
  - G5: TokenBudgetPlanner 确认已存在并与 PromptBuilder/ContextPruner 完整集成
- Files created/modified:
  - backend/src/main/java/com/medora/memory/service/MemoryService.java (modified)
  - backend/src/main/java/com/medora/memory/domain/UserMemoryProfileEntity.java (modified)
  - backend/src/main/java/com/medora/memory/service/UserMemoryProfileRepository.java (modified)
  - backend/src/main/java/com/medora/knowledge/infra/AsyncConfig.java (modified)
- **Test results:** 31/31 tests passed, BUILD SUCCESS

### Phase RAG-3: 工程优化与测试（待开始）
- **Status:** pending

### Phase RAG-3: 工程优化与测试（待开始）
- **Status:** pending

## Test Results
| Test | Input | Expected | Actual | Status |
|------|-------|----------|--------|--------|
| (尚未开始实施，测试待执行) | | | | |

## Error Log
| Timestamp | Error | Attempt | Resolution |
|-----------|-------|---------|------------|
| (暂无) | | | |

## 5-Question Reboot Check
| Question | Answer |
|----------|--------|
| Where am I? | 已完成差距分析，等待用户确认启动顺序 |
| Where am I going? | 3 个 Phase 实施（RAG-1 基础加固 → RAG-2 记忆增强 → RAG-3 工程优化） |
| What's the goal? | 补齐 15 项差距，使 RAG 系统与设计文档对齐 |
| What have I learned? | See findings.md |
| What have I done? | 完成全量差距分析并输出优化计划 |
