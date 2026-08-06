# Phase 8 网页端产品原型日志

## 1. 本轮目标
- 回到最初设计文档和现有代码能力，确认 Medora 正式网页端应该开发哪些页面。
- 不继续直接修补现有联调页，而是先产出一版可评审的网页端原型。
- 让登录分流、用户工作台、管理员工作台这三层结构先被确认下来。

## 2. 设计与代码梳理结论

### 2.1 角色与权限
- 后端已经通过 `roles` 向前端暴露角色信息。
- `/api/v1/admin/**` 已经是管理员专属接口。
- 这意味着前端可以正式实现：
  - 统一登录页
  - 登录后按角色自动跳转
  - 用户与管理员两套独立工作区

### 2.2 现有后端已足够支撑的页面能力
- 用户侧：
  - 登录 / 注册 / 当前用户
  - 科室 / 医生 / 号源查询
  - 预约创建 / 取消 / 列表 / 详情
  - 会话列表 / 消息列表 / SSE 对话
- 管理员侧：
  - 后台总览基础能力
  - 知识库管理
  - 会话查看
  - Agent Trace 查看
  - 审计日志查看

结论：
- 现在最缺的不是接口，而是正式网页端的信息架构和页面组织方式。

## 3. 正式网页端页面清单方案

### 3.1 公共层
- `/`
- `/login`

### 3.2 用户工作台
- `/app/dashboard`
- `/app/appointments`
- `/app/assistant`
- `/app/services`
- `/app/profile`

### 3.3 管理工作台
- `/admin`
- `/admin/knowledge`
- `/admin/conversations`
- `/admin/traces`
- `/admin/audit-logs`
- `/admin/system` 预留

详细说明文档已新增：
- `design/07-web-product-page-map-and-prototype-plan.md`

## 4. 首轮原型稿落地

### 4.1 原型总览与登录页
- `/prototype`
- `/prototype/login`

### 4.2 用户工作台原型
- `/prototype/patient/dashboard`
- `/prototype/patient/appointments`
- `/prototype/patient/assistant`
- `/prototype/patient/services`
- `/prototype/patient/profile`

### 4.3 管理工作台原型
- `/prototype/admin/dashboard`
- `/prototype/admin/knowledge`
- `/prototype/admin/conversations`
- `/prototype/admin/traces`
- `/prototype/admin/audit`

### 4.4 共享壳层
- 新增 `frontend/components/prototype-workspace-shell.tsx`
- 统一承接：
  - 左侧导航
  - 页面 Hero
  - 评审重点区
  - 右侧辅助信息区

## 5. 验证

### 5.1 类型检查
- 命令：`npx tsc --noEmit`
- 结果：通过

### 5.2 生产构建
- 命令：`$env:NEXT_DIST_DIR='.next-buildcheck'; npm run build`
- 结果：通过

### 5.3 构建清理
- 删除了临时构建目录 `.next-buildcheck`
- 恢复了：
  - `frontend/tsconfig.json`
  - `frontend/next-env.d.ts`

## 6. 本轮没有做的事情
- 没有启动前后端服务做真实联调。
- 没有调用真实 AI API。
- 没有把这组原型直接接回正式业务页。

原因：
- 这轮任务重点是先确认产品结构与页面原型。
- 只有当原型方向被确认后，真实前端开发和联调才值得继续推进。

## 7. 本轮结论
- 现有正式网页端不应继续沿着零散测试页演进。
- 正确方向应是“统一登录 + 用户工作台 + 管理员工作台”。
- 用户端和管理员端都应该以左侧导航组织核心功能。
- 现在已经具备可评审的首轮原型稿，下一步应由你先确认页面结构和视觉方向，再开始正式开发。
