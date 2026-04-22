<<<<<<< HEAD
# Medora

Medora 是一个面向医疗服务场景的工程化智能助手系统。当前仓库处于从设计走向实现的早期阶段，已完成总体设计、数据库设计、API 契约、Agent/RAG/Memory 设计和实施计划文档，当前正在按阶段执行 `Phase 0：工程初始化`。

## 当前状态

- 设计文档已完成第一轮落地，位于 [design](./design)
- 后端最小 Spring Boot 工程骨架已创建
- 前端最小 Next.js 工程骨架已创建
- 根目录环境模板已创建
- 尚未开始大规模业务代码实现

## 仓库结构

```text
Medora/
  ├─ design/      # 设计文档
  ├─ backend/     # Spring Boot 后端
  ├─ frontend/    # Next.js 前端
  ├─ deploy/      # Docker / Nginx / Compose 预留
  ├─ docs/        # 运行与补充文档预留
  ├─ scripts/     # 辅助脚本预留
  ├─ .env.example
  └─ README.md
```

## 设计文档

- [技术详细设计](./design/technical-design.md)
- [领域模型与数据库设计](./design/02-domain-db-design.md)
- [API 契约设计](./design/03-api-contract-design.md)
- [Agent / RAG / Memory 设计](./design/04-agent-rag-memory-design.md)
- [实施计划](./design/05-implementation-plan.md)

## Phase 0 目标

当前阶段只完成以下内容：

- 建立仓库基础目录结构
- 初始化后端工程
- 初始化前端工程
- 补充环境变量模板
- 建立基础 README 与开发约定

本阶段不包含：

- 完整业务功能
- 真实数据库接入
- 真实认证流程
- 真实 RAG / Agent 实现

## 本地启动建议

### 后端

进入 `backend` 后，使用 Maven 启动：

```bash
mvn spring-boot:run
```

默认健康检查接口：

```text
GET http://localhost:8080/actuator/health
GET http://localhost:8080/api/v1/health
```

### 前端

进入 `frontend` 后安装依赖并启动：

```bash
npm install
npm run dev
```

默认访问：

```text
http://localhost:3000
```

## 下一步

下一阶段建议进入 `Phase 1：数据库与主数据`，重点是：

- 初始化 migration
- 建立用户、科室、医生、号源等基础表
- 打通主数据查询 API

=======
# medora
>>>>>>> fa11bc50eb98f4a896f3ba66d69d9886d88d6a33
