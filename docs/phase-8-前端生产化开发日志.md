# Phase 8 前端生产化开发日志

## 1. 本轮目标

- 以高保真原型（`/prototype/*`）为视觉蓝本，将联调页升级为可发布的正式生产页面。
- 采用三栏式工作台布局：深色 teal 左侧导航栏（280px）+ 主内容区 + 右侧信息栏（320px）。
- 用户端统一到 `/app/*` 路由命名空间，管理端保留 `/admin/*` 路由。
- 登录后按角色自动分流：USER → `/app/dashboard`，ADMIN → `/admin`。
- 分两批交付：Batch 1 用户端 5 页 + 登录页，Batch 2 管理端 5 页。

## 2. 设计决策

| 决策 | 选项 | 结论 |
|------|------|------|
| 布局方式 | 三栏式 / 顶部导航 | 三栏式工作台布局，沿用原型视觉方案 |
| 用户端路由 | `/app/*` / 保持原路由 | `/app/*`，与原型 `/prototype/patient/*` 对齐 |
| 共享布局 | layout.tsx / 每页自包裹 | 每页自管理 `WorkspaceShell` 包裹，与现有自包含模式一致 |
| 对话列表 | 左侧列 / 横向滚动 | 助手页改为横向滚动条，因左侧已被导航栏占据 |
| 类型管理 | 页面内联 / 集中定义 | 集中到 `lib/types/api.ts`，消除跨页面重复 |

## 3. 实施内容

### 3.1 Batch 0 — 基础设施层

新建共享组件和工具：

| 文件 | 用途 |
|------|------|
| `frontend/lib/utils.ts` | `joinClasses` 工具函数 |
| `frontend/lib/types/api.ts` | 集中定义 API 响应类型：Department、Doctor、ScheduleSlot、Appointment、Conversation、ChatMessage、CurrentUser、LoginResult 等 |
| `frontend/lib/hooks/use-auth.ts` | 认证守卫 hook：检查 token → 获取 /users/me → 角色校验 → 自动重定向 |
| `frontend/lib/hooks/use-async-action.ts` | 统一 loading/error 状态管理 |
| `frontend/components/badge.tsx` | 徽标组件，支持 default/solid/soft/success/warning tone |
| `frontend/components/panel.tsx` | 面板组件，含 eyebrow/title/description/actions |
| `frontend/components/metric-card.tsx` | 指标卡片，含可选 trend/trendLabel |
| `frontend/components/workspace-shell.tsx` | 核心三栏布局壳层，支持 patient/admin 双角色导航 |

### 3.2 Batch 1 — 登录页 + 用户端

| 文件 | 操作 | 说明 |
|------|------|------|
| `frontend/app/login/page.tsx` | 重写 | 全屏分屏布局，左侧品牌面板 + 右侧登录/注册表单，角色自动跳转 |
| `frontend/app/app/dashboard/page.tsx` | 新建 | 个性化问候、指标卡片、下一步建议、右侧本周预约 |
| `frontend/app/app/appointments/page.tsx` | 迁移重构 | 从旧 `/appointments` 迁移完整预约流程，默认科室优先逻辑 |
| `frontend/app/app/assistant/page.tsx` | 迁移重构 | 从旧 `/chat` 迁移 SSE 流式对话，会话列表改为横向滚动条 |
| `frontend/app/app/services/page.tsx` | 新建 | 预约+会话合并时间线视图 |
| `frontend/app/app/profile/page.tsx` | 新建 | 个人资料展示 + 就诊偏好编辑，默认科室改为下拉选择 |

### 3.3 Batch 2 — 管理端

| 文件 | 操作 | 说明 |
|------|------|------|
| `frontend/app/admin/page.tsx` | 重写 | 管理控制台首页：指标卡片 + 快捷入口 + 系统状态 |
| `frontend/app/admin/knowledge/page.tsx` | 重写 | 文档上传、索引管理、检索验证 |
| `frontend/app/admin/conversations/page.tsx` | 重写 | 会话筛选、详情、长期记忆、消息流 |
| `frontend/app/admin/traces/page.tsx` | 重写 | Trace 筛选、详情、检索结果/工具事件 JSON |
| `frontend/app/admin/audit-logs/page.tsx` | 重写 | 审计日志筛选、分页、详情 JSON |

### 3.4 Batch 3 — 路由清理

| 文件 | 操作 | 说明 |
|------|------|------|
| `frontend/app/page.tsx` | 修改 | 更新首页链接指向 `/app/*` 路由 |
| `frontend/app/appointments/page.tsx` | 替换 | 重定向到 `/app/appointments` |
| `frontend/app/chat/page.tsx` | 替换 | 重定向到 `/app/assistant` |

### 3.5 未修改的文件

以下文件保持原样，不做任何修改：

- 所有原型页面 `frontend/app/prototype/*`
- `frontend/lib/api/client.ts` — apiFetch
- `frontend/lib/auth/token.ts` — token 管理
- `frontend/lib/sse/chat-stream.ts` — SSE 流式解析
- `frontend/styles/globals.css` — CSS 设计变量
- `frontend/components/admin-shell.tsx`、`frontend/components/patient-shell.tsx` — 保留参考

## 4. 非生产内容审计

用户明确要求页面为发布品质，不包含开发者提示、调试信息和评测内容。共排查修复 33 处：

- 移除"联调提醒"面板，替换为"最近事件"
- 移除 Prometheus actuator 原始链接
- 移除 chat 状态中暴露的 traceId
- 移除展示给用户的内部工具名称和状态码
- 移除"请确认后端服务已启动"等调试提示
- 移除自描述文案"更像真实服务的医疗对话"
- 清除表单预填测试默认值
- 将 "Summary JSON" / "Business State" 等英文标签改为中文
- 将原始字段名占位符改为中文描述

## 5. 后端修复

### 5.1 DoctorRepository JPQL 类型推断错误

- **文件**：`backend/src/main/java/com/medora/masterdata/repository/DoctorRepository.java`
- **现象**：`GET /api/v1/doctors` 返回 500，PostgreSQL 报 `函数 lower(bytea) 不存在`
- **原因**：JPQL 中 `concat('%', :keyword, '%')` 当 `keyword` 为 `null` 时，JDBC 驱动无法推断参数类型，PostgreSQL 将其视为 `bytea`
- **修复**：改为 `concat('%', cast(:keyword as string), '%')`，显式指定参数类型

### 5.2 管理员账户缺少 user_profile 记录

- **现象**：管理员登录返回 404 `USER_NOT_FOUND: user profile not found`
- **原因**：手动插入 `user_account` 记录时未创建关联的 `user_profile` 记录
- **修复**：为 admin 账户（user_id=2）补建 `user_profile` 记录

### 5.3 管理员账户密码哈希不匹配

- **现象**：管理员登录返回 401
- **原因**：使用了网上常见的 BCrypt 示例哈希值，该哈希不对应密码 "123456"
- **修复**：从已有用户（通过注册 API 创建的 demo@example.com）复制正确的 BCrypt 哈希

## 6. 验证

### 6.1 生产构建

```
npm run build
```

结果：通过，29 条路由，0 错误。

### 6.2 端到端功能验证

| 验证项 | 结果 |
|--------|------|
| 登录页 → 注册/登录 → 角色跳转 | 通过 |
| USER 登录 → `/app/dashboard` 个性化问候 | 通过 |
| 预约中心 → 科室/医生/号源筛选 → 创建预约 | 通过 |
| 导诊助手 → SSE 流式对话 → 会话管理 | 通过 |
| 我的服务 → 时间线展示 | 通过 |
| 个人档案 → 信息展示/偏好编辑 | 通过 |
| ADMIN 登录 → `/admin` 管理控制台 | 通过 |
| 知识库 → 上传/发布/检索验证 | 通过 |
| 会话查看 → 筛选/详情/记忆/消息 | 通过 |
| 链路追踪 → 筛选/详情 | 通过 |
| 审计日志 → 筛选/分页 | 通过 |
| 侧边栏导航切换 | 通过 |
| 旧路由 `/appointments` → 重定向 | 通过 |
| 旧路由 `/chat` → 重定向 | 通过 |
| `/api/v1/doctors` 查询不再报错 | 通过 |

## 7. 文件清单

### 新建文件（13 个）

```
frontend/lib/utils.ts
frontend/lib/types/api.ts
frontend/lib/hooks/use-auth.ts
frontend/lib/hooks/use-async-action.ts
frontend/components/badge.tsx
frontend/components/panel.tsx
frontend/components/metric-card.tsx
frontend/components/workspace-shell.tsx
frontend/app/app/dashboard/page.tsx
frontend/app/app/appointments/page.tsx
frontend/app/app/assistant/page.tsx
frontend/app/app/services/page.tsx
frontend/app/app/profile/page.tsx
```

### 修改文件（10 个）

```
frontend/app/login/page.tsx              — 重写为分屏登录页
frontend/app/page.tsx                    — 更新链接
frontend/app/appointments/page.tsx       — 替换为重定向
frontend/app/chat/page.tsx               — 替换为重定向
frontend/app/admin/page.tsx              — 重写管理控制台首页
frontend/app/admin/knowledge/page.tsx    — 重写知识库管理页
frontend/app/admin/conversations/page.tsx — 重写会话查看页
frontend/app/admin/traces/page.tsx       — 重写链路追踪页
frontend/app/admin/audit-logs/page.tsx   — 重写审计日志页
backend/.../DoctorRepository.java        — 修复 JPQL 类型推断
```

## 8. AI 模型配置链路修复

### 8.1 问题描述

导诊助手页面发送消息后，后端日志显示 chat stream 请求在 5-107ms 内即返回 200，无任何 AI 模型调用日志。说明 `AgentModelGateway` 的 `resolveChatProvider()` 仍返回 `"local"`，未命中 `"anthropic-compatible"` 分支。

### 8.2 根因分析

链路追踪发现三个独立问题叠加导致 `.env` 配置未能注入 Spring 环境：

**问题 1 — 依赖制品错误**

`pom.xml` 中使用了 `spring-dotenv`（纯 Spring Framework 核心），而非 `springboot3-dotenv`（Spring Boot 3 自动集成）。前者不提供 Spring Boot 自动配置，`.env` 文件不会被加载为 PropertySource。

**问题 2 — `springdotenv.directory` 配置方式错误**

`springdotenv.*` 配置属性必须在 Spring 读取 `application.yml` 之前生效，因此只能通过 **JVM 系统属性** 或 **OS 环境变量** 设置。将 `spring.dotenv.directory` 写在 `application.yml` 中无效——dotenv 加载器在解析 yml 之前就已经初始化了。

**问题 3 — JVM 参数未传递到 Spring Boot 应用进程**

`start-backend.bat` 通过 `mvn spring-boot:run` 启动后端。`-Dxxx` 只设置 Maven 自身 JVM 的系统属性，不会传递给 `spring-boot:run` fork 出的应用进程。正确做法是使用 Maven 属性 `spring-boot.run.jvmArguments`。

### 8.3 修复内容

| 文件 | 修改 |
|------|------|
| `backend/pom.xml` | 依赖从 `spring-dotenv` 改为 `springboot3-dotenv:5.1.0` |
| `backend/.../application.yml` | 移除无效的 `spring.dotenv.directory` 配置 |
| `scripts/dev/start-backend.bat` | 添加 `-Dspring-boot.run.jvmArguments="-Dspringdotenv.directory=%PROJECT_ROOT%"`，显式指向项目根目录 `.env` 文件 |
| `backend/.../MedoraBackendApplication.java` | 添加启动诊断日志，打印 `AiProperties` 中 chatProvider/chatModel/chatBaseUrl/chatApiKey 的实际值（部分脱敏），便于排查配置加载问题 |

### 8.4 配置链路

修复后完整的配置加载链路：

```
start-backend.bat
  → -Dspring-boot.run.jvmArguments="-Dspringdotenv.directory=D:\Aproject\medora"
    → Spring Boot 应用启动时 springboot3-dotenv 从 D:\Aproject\medora\.env 加载
      → .env 中的 AI_CHAT_* 变量进入 Spring PropertySource
        → application.yml 的 ${AI_CHAT_API_KEY:} 正确解析
          → AiProperties 获得 chatApiKey / chatBaseUrl / chatModel
            → AgentModelGateway.resolveChatProvider() 返回 "anthropic-compatible"
              → 实际调用 https://182.139.39.10:40009 上的 glm-5.1 模型
```

### 8.5 验证

重启后端后，日志中 `[DIAG]` 行输出：

```
chatProvider='anthropic-compatible', chatModel='glm-5.1',
chatBaseUrl='https://182.139.39.10:40009',
chatApiKey='sk-lbf1...858ba5'
```

在导诊助手页面发送消息，后端日志出现模型调用耗时（秒级），前端收到流式回复。AI 模型链路验证通过。

## 9. 当前结论

- 前端 11 个正式页面已完成生产化改造，全部接入真实后端 API。
- 用户端和管理端均采用三栏式工作台布局，视觉风格统一。
- 非生产内容已全部清除，页面达到发布品质。
- 后端 DoctorRepository 类型推断 bug 已修复。
- AI 模型配置链路已打通，`.env` 文件通过 `springboot3-dotenv` 正确加载，导诊助手可正常调用真实 AI 模型。
- Medora 现已具备完整的可演示产品形态。
