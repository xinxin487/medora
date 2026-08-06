# Phase 5 开发日志

## 基本信息
- 阶段：Phase 5 知识库与基础 RAG
- 完成日期：2026-05-12
- 对应计划：[design/05-implementation-plan.md](/D:/BOS/Medora/design/05-implementation-plan.md)

## 本阶段目标
- 打通知识文档上传、解析、切片、入库与检索测试接口。
- 让对话链路能够读取已发布知识库并返回引用事件。
- 补齐最小后台联调页与对话引用展示。

## 已完成开发内容
- 新增数据库迁移 `V7__init_knowledge_tables.sql`：
  - `knowledge_document`
  - `knowledge_chunk`
  - `knowledge_ingest_task`
  - `knowledge_chunk.content_tsv` 生成列与 GIN 索引
- 补充数据库迁移：
  - `V8__add_knowledge_embedding_vector.sql`
    - 为 `knowledge_chunk` 增加 `embedding vector(1024)` 列
    - 增加 `ivfflat` 向量索引
  - `V9__resize_knowledge_embedding_dimension.sql`
    - 统一开发环境向量维度为 `1024`
    - 重建 `idx_knowledge_chunk_embedding_ivfflat`
- 新增后端知识库模块：
  - `AdminKnowledgeController`
  - `KnowledgeDocumentService`
  - `DocumentIngestService`
  - `RetrievalService`
  - `DocumentParser`
  - `DocumentChunker`
  - `KeywordRetriever` / `VectorRetriever` / `HybridRetriever`
  - knowledge 相关 Entity、Repository、DTO
- 新增后台知识库 API：
  - `POST /api/v1/admin/knowledge/documents/upload`
  - `GET /api/v1/admin/knowledge/documents`
  - `GET /api/v1/admin/knowledge/documents/{documentId}`
  - `POST /api/v1/admin/knowledge/documents/{documentId}/publish`
  - `POST /api/v1/admin/knowledge/documents/{documentId}/rebuild`
  - `POST /api/v1/admin/knowledge/search/test`
- 对话 SSE 接入基础 RAG：
  - 命中已发布知识时发送 `citation` 事件。
  - 助手消息落库时写入 `citation_json`。
  - 未命中知识时保留 Phase 4 stub 回复并明确无知识引用。
- 新增前端页面：
  - `frontend/app/admin/knowledge/page.tsx`
  - 支持上传文本/Markdown 文档、发布/下线、检索测试。
  - `frontend/app/chat/page.tsx` 支持展示流式与落库后的引用片段。
  - 聊天页阶段文案已更新为 `Phase 5 Chat`。
- 更新配置：
  - `medora.phase` 更新为 `phase-5`。

## 自测与联调记录
### 1. 后端单元测试
- 验证命令：`mvn test`
- 验证结果：通过
- 测试统计：`13 tests, 0 failures, 0 errors`
- 新增覆盖重点：
  - 文档切片逻辑。
  - 空查询检索保护。
  - 对话运行时构造器适配 RAG 依赖。

### 2. 前端构建
- 验证命令：`npm run build`
- 验证结果：通过
- 新增验证页面：
  - `/admin/knowledge`
  - `/chat` 引用展示

### 3. 数据库迁移与后端启动
- 验证方式：使用 `scripts/dev/start-backend.bat` 启动。
- 验证结果：
  - Flyway 成功验证 `9 migrations`。
  - 当前 schema 版本为 `v9`。
  - Hibernate/JPA 初始化成功。
  - Tomcat 成功启动到 `8080`。

### 4. 健康检查与端到端 HTTP 联调
- 联调日期：2026-05-12
- 后端健康检查：
  - 验证地址：`http://localhost:8080/api/v1/health`
  - 验证结果：返回 `200`
  - 验证重点：`phase=phase-5`，`status=UP`
- 前端页面 HTTP 检查：
  - `/` 返回 `200`
  - `/admin/knowledge` 返回 `200`
  - `/chat` 返回 `200`
- 实际验证链路：
  - `POST /api/v1/auth/register`
  - 本地数据库将联调账号 `register_source` 标记为 `ADMIN`
  - `POST /api/v1/auth/login`
  - `POST /api/v1/admin/knowledge/documents/upload`
  - `POST /api/v1/admin/knowledge/documents/{documentId}/publish`
  - `POST /api/v1/admin/knowledge/search/test`
  - `POST /api/v1/chat/conversations`
  - `POST /api/v1/chat/conversations/{conversationId}/stream`
  - `GET /api/v1/chat/conversations/{conversationId}/messages`
- 联调结果：
  - 联调账号登录角色为 `ADMIN`。
  - 知识文档上传成功，返回 `200`。
  - 测试文档切片数量：`1`。
  - 文档发布状态：`PUBLISHED`。
  - 后台检索测试命中：`1` 条。
  - SSE 返回 `200`，包含 `citation` 与 `message_end`。
  - 消息列表返回 `2` 条消息，助手消息 `citation_json` 已持久化。

### 5. 补充联调与修复记录
- 联调日期：2026-05-12
- 向量模型与向量库验证：
  - 联调环境已验证 `openai-compatible` 模式可调用 SiliconFlow `Qwen/Qwen3-Embedding-8B`。
  - 本次联调使用的关键环境变量配置为：
    - `AI_PROVIDER=openai-compatible`
    - `AI_BASE_URL=https://api.siliconflow.cn/v1`
    - `AI_EMBEDDING_MODEL=Qwen/Qwen3-Embedding-8B`
    - `AI_EMBEDDING_DIMENSIONS=1024`
    - `AI_API_KEY` 通过环境变量注入，未在仓库中明文保存
  - 后端默认配置已支持上述环境变量覆盖，默认回退值位于 `application.yml`。
  - 数据库向量环境基于 `pgvector` 扩展，`V8__add_knowledge_embedding_vector.sql` 中已显式执行 `CREATE EXTENSION IF NOT EXISTS vector;`。
  - 文档切片 embedding 已写入 `knowledge_chunk.embedding vector(1024)`。
  - 检索链路为 `KeywordRetriever + VectorRetriever + HybridRetriever` 混合召回，不再是纯占位实现。
- 运行时兼容性修复：
  - `EmbeddingService` 已在 openai-compatible `/embeddings` 请求体中补充 `dimensions` 字段，避免上游默认返回 `4096` 维导致 `AI_EMBEDDING_DIMENSION_MISMATCH`。
  - 本地默认配置、数据库列类型与向量索引统一为 `1024` 维，避免 `ivfflat` 因维度超限导致 Flyway 启动失败。
- 检索质量修复：
  - `RetrievalService` 增加结果相关性闸门，避免“你好”“我最近咳嗽，应该挂哪个科？”这类无关问题强行召回“取消挂号”类片段。
  - 相关问题如“取消挂号”仍可正常命中文档标题、正文或向量近邻结果。
- 可观测性与稳定性修复：
  - `GlobalExceptionHandler` 增加未处理异常日志，记录 `requestId`、`METHOD URI` 和完整堆栈，便于定位 `COMMON_INTERNAL_ERROR` 的真实根因。
  - 管理后台知识库检索修复了 `Instant -> OffsetDateTime` 投影异常；原因是 Spring Data native projection 与 `uploaded_at` 字段类型不匹配，现已移除检索链路中未使用的 `uploadedAt` 投影字段。

## 过程中发现并处理的问题
- 问题：中文短句直接依赖 PostgreSQL `simple` FTS 命中不稳定。
- 处理：保留 `content_tsv` 与 GIN 索引，同时在检索 SQL 中增加 `ILIKE` 兜底评分；在此基础上继续保留向量召回与混合排序，保证一期演示既能兜住中文短句，也能使用真实 embedding 检索。

- 问题：前端 `apiFetch` 对 `FormData` 上传误加 `application/json`。
- 处理：识别 `FormData` 时不设置 `Content-Type`，交给浏览器自动生成 multipart boundary。

- 问题：向量维度一度被调整到 `4096`，导致 PostgreSQL `ivfflat` 无法为该列建索引，Flyway 在启动阶段失败。
- 处理：将数据库迁移、默认配置和运行时维度统一收敛到 `1024`，并保持与 `Qwen/Qwen3-Embedding-8B` 请求参数一致。

- 问题：管理员知识库检索偶发返回 `COMMON_INTERNAL_ERROR`，前端只能看到通用错误码，难以定位。
- 处理：补充全局异常日志，并修复检索投影中的时间类型不匹配问题，消除 `Cannot project java.time.Instant to java.time.OffsetDateTime` 异常。

- 问题：混合检索在样本库较小时容易“有问必答”，把不相关问题错误召回到已有文档。
- 处理：在 `RetrievalService` 增加词面相关性校验，对无足够重合词的结果直接过滤。

## 阶段产出结论
- Phase 5 已完成最小可运行闭环：
  - 管理员上传知识文档。
  - 文档解析、切片、入库。
  - 管理员发布知识。
  - 后台检索测试。
  - 对话基于已发布知识返回引用。
- Phase 5 当前已具备真实向量检索基础：
  - 可接入外部 embedding 模型。
  - 向量写入 pgvector `vector` 列。
  - 关键词与向量混合召回已在联调中验证通过。

## 已知遗留项
- 当前已接入真实 embedding 与 pgvector 混合检索，但相关性阈值、意图路由和召回评估策略仍有继续优化空间。
- 文档解析当前优先支持 UTF-8 文本与 Markdown，PDF/DOCX 解析尚未接入。
- `rebuild` 接口当前保留契约占位，未重新读取原始文件执行重建。
- 管理后台页面依赖管理员账号；当前注册接口默认创建普通用户，管理员账号仍需通过数据库或后续后台能力准备。

## 下一阶段建议
- 进入 Phase 6 Agent 编排与 Tool Calling。
- 优先实现：
  - `ToolRegistry` / `ToolExecutor`。
  - 查询科室、医生、号源工具。
  - 预约、取消预约动作工具。
  - 将 RAG 引用、工具状态和最终回复纳入统一编排链路。
