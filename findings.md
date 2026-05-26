# Findings — RAG 系统差距分析

## Research Findings

### 当前 RAG 系统架构
- **文档摄入**: 同步阻塞，上传→解析→切分→向量化→存储 全部在 HTTP 线程完成
- **文档存储**: 原始文件不落盘，从 MultipartFile 直接解析 UTF-8 文本
- **文档解析**: 仅支持 .md 和 .txt，其余类型标记为 "MANUAL"
- **文本清洗**: 内联在 DocumentChunker.clean() 中（去空字符、合并空白、合并换行）
- **文档切分**: 700 字符窗口 + 120 字符 overlap，标题感知，按双换行分段
- **向量化**: 双模式 — local-hash（开发用 n-gram 哈希） / openai-compatible（HTTP 调用）
- **关键词检索**: PostgreSQL FTS (`content_tsv @@ plainto_tsquery('simple', query)`) + LIKE 组合评分
- **向量检索**: pgvector 余弦距离 (`<=>` 操作符，1 - distance)
- **混合融合**: 0.55 关键词 + 0.45 向量加权，阈值 0.15
- **重排**: ❌ 不存在
- **长期记忆**: 4 种类型，仅 BOOK_APPOINTMENT 成功触发写入
- **会话摘要**: 12 条消息触发，含业务状态追踪

### 关键差距
1. **MinIO 未使用** — 原始文件不持久化，重建需重新上传
2. **同步摄入** — 大文档可能 HTTP 超时
3. **仅支持 .md/.txt** — 医疗 PDF 政策文件无法直接使用
4. **无 RerankService** — 设计留有接口但未实现
5. **记忆系统不完整** — 置信度/遗忘/多触发均未实现
6. **无 TokenBudgetPlanner** — Prompt 裁剪无量化依据

### 测试覆盖现状
- DocumentChunkerTest: 1 测试
- RetrievalServiceTest: 3 测试
- MemoryServiceTest: 1 测试
- ConversationSummaryServiceTest: 1 测试
- 无 DocumentParserTest、EmbeddingServiceTest

## Technical Decisions

| Decision | Rationale |
|----------|-----------|
| KnowledgeFileStore 封装 MinIO | 统一文件存储入口，文档上传/重建均可复用 |
| Spring @Async 实现异步摄入 | 无需引入消息队列，与当前技术栈一致 |
| Apache Tika 做文档解析 | 统一库支持 PDF/Word/Excel，减少维护多个 SDK 的成本 |
| RerankService 接口 + NoopReranker 默认实现 | 遵循设计文档"预留标准接口"，二期可替换 cross-encoder |
| 置信度评分走规则引擎而非模型 | 一期规则足够，避免增加模型调用成本和延迟 |

## Resources

- 差距分析报告: docs/RAG系统差距分析与优化计划.md
- 原始设计文档: design/03-api-contract-design.md
- 原始设计文档: design/04-agent-rag-memory-design.md
