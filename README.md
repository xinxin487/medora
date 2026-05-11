# Medora

Medora 是一个面向医疗服务场景的工程化智能助手系统。当前项目已经完成 Phase 0 到 Phase 3 的核心开发，具备本地可运行的前后端、数据库迁移、用户认证、主数据查询，以及预约创建/取消闭环能力。

## 当前进度

- Phase 0：工程骨架、前后端基础项目、环境约定已完成。
- Phase 1：数据库 migration、科室/医生/号源主数据查询已完成。
- Phase 2：注册、登录、JWT 鉴权、当前用户接口已完成。
- Phase 3：预约创建、幂等提交、预约列表/详情、取消预约、号源库存扣减与回补已完成。
- 下一阶段：Phase 4，会话、消息与 SSE 基础流式链路。

## 仓库结构

```text
Medora/
  design/      # 设计文档与实施计划
  backend/     # Spring Boot 后端
  frontend/    # Next.js 前端
  deploy/      # Docker / Nginx / Compose 预留
  docs/        # 阶段日志与补充文档
  scripts/     # 项目辅助脚本
  logs/        # 本地启动日志，默认不提交
  README.md
```

## 本地环境

后端编译和测试请固定使用项目指定的 Java 21 与 Maven：

```bat
set "JAVA_HOME=D:\BOS\medoraresourse\jdk-21.0.10"
set "MAVEN_HOME=D:\BOS\medoraresourse\apache-maven-3.9.15"
set "PATH=%MAVEN_HOME%\bin;%JAVA_HOME%\bin;%PATH%"
```

前端进入 `frontend` 后直接启动：

```bat
npm run dev
```

## 启动脚本

项目提供了本地开发启动脚本：

```bat
scripts\dev\start-backend.bat
scripts\dev\start-frontend.bat
```

默认日志输出：

```text
logs/backend-dev.log
logs/backend-dev.err.log
logs/frontend-dev.log
logs/frontend-dev.err.log
```

## 常用验证

后端测试：

```bat
cd backend
set "JAVA_HOME=D:\BOS\medoraresourse\jdk-21.0.10"
set "MAVEN_HOME=D:\BOS\medoraresourse\apache-maven-3.9.15"
set "PATH=%MAVEN_HOME%\bin;%JAVA_HOME%\bin;%PATH%"
mvn -Dmaven.repo.local=D:\BOS\Medora\.m2 test
```

前端构建：

```bat
cd frontend
npm run build
```

本地访问：

```text
后端健康检查：http://localhost:8080/api/v1/health
前端首页：http://localhost:3000
预约页面：http://localhost:3000/appointments
```

## Phase 3 核心能力

- `POST /api/v1/appointments`：创建预约，支持 `Idempotency-Key` 幂等提交。
- `GET /api/v1/appointments`：查询我的预约列表。
- `GET /api/v1/appointments/{appointmentId}`：查询预约详情。
- `POST /api/v1/appointments/{appointmentId}/cancel`：取消预约并回补号源库存。
- 预约操作会写入 `appointment_operation_log`。

## 文档入口

- [实施计划](./design/05-implementation-plan.md)
- [API 契约设计](./design/03-api-contract-design.md)
- [阶段文档索引](./docs/README.md)
- [Phase 3 开发日志](./docs/phase-3-%E5%BC%80%E5%8F%91%E6%97%A5%E5%BF%97.md)

## 已验证结果

- 后端测试通过：`9 tests, 0 failures, 0 errors`
- 前端构建通过：`npm run build`
- Phase 3 联调通过：注册登录、查询号源、创建预约、重复幂等提交、查询列表/详情、取消预约、库存 `8 -> 7 -> 8`
