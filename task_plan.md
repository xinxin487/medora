# Task Plan: RAG 系统优化实施

## Goal

按照 docs/RAG系统差距分析与优化计划.md 中定义的 3 个 Phases，逐步补齐当前 RAG 系统与原始设计文档之间的 15 项差距，完善文档接入、检索质量、记忆系统和测试覆盖。

## Current Phase

Phase RAG-1（已完成，待用户确认是否继续 RAG-2）

## Phases

### Phase RAG-1: 基础加固（P0）
- [x] G1: 原始文件持久化到 MinIO
- [x] G4: 异步摄入
- [x] G2: PDF/Office 解析支持
- [x] G8: RerankService 占位接口
- **Status:** complete

### Phase RAG-2: 检索与记忆增强（P1）
- [ ] G12: 记忆写入多触发
- [ ] G13: 置信度评分机制
- [ ] G14: 记忆过期机制
- [ ] G5: TokenBudgetPlanner
- **Status:** pending

### Phase RAG-3: 工程优化与测试（P2）
- [ ] G6: FAQ 单元切分
- [ ] G7: 切分参数可配置
- [ ] G9: RetrievalResultAssembler
- [ ] G11: 中文分词增强
- [ ] G15: 测试覆盖增强
- **Status:** pending

## Key Questions

1. 是否优先实施 P0 的持久化和异步改造，还是从影响较小的 P2 开始？
2. MinIO 已经搭建运行中（.env.example 有配置），是否确认可用？
3. PDF/Office 解析引入 Apache Tika 还是分别引入 PDFBox + POI？

## Decisions Made

| Decision | Rationale |
|----------|-----------|
| 分 3 个 Phase 实施 | 与差距分析文档对齐，P0 解决数据持久性和稳定性风险，P1 完善功能，P2 工程优化 |

## Errors Encountered

| Error | Attempt | Resolution |
|-------|---------|------------|
| (暂无) |         |            |

## Notes

- 所有 Phase 详见 docs/RAG系统差距分析与优化计划.md
- 每个 Phase 完成后需运行 `mvn test` 确保回归
