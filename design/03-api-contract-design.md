# 03. API 契约详细设计

## 0. 文档定位与命名说明
本文是对 [technical-design.md](/d:/BOS/Medora/design/technical-design.md) 与 [02-domain-db-design.md](/d:/BOS/Medora/design/02-domain-db-design.md) 的继续细化，目标是把接口层补充为可直接指导以下工作的设计文档：

- 定义 Controller
- 定义 Request DTO / Response VO
- 定义统一响应结构
- 定义错误码枚举
- 指导前后端联调
- 指导 Agent Tool 入参与返回结构设计

**命名说明**
- 你在需求里建议的文件名与已有 `02-domain-db-design.md` 重名。
- 为避免覆盖上一份数据库设计文档，本文按合理工程假设新建为 `design/03-api-contract-design.md`。

**文档范围**
- 一期 MVP 所需接口契约
- 不进入大规模业务代码实现
- 保持与既有架构、数据库、领域模型方案一致

---

## 1. 设计目标与约束

### 1.1 设计目标
- 保证接口层职责边界清晰，避免 Controller 直接承载复杂业务规则。
- 统一请求、响应、分页、错误码、鉴权、SSE 事件协议。
- 使前端页面、管理后台、Agent 编排、Service 层都围绕同一套契约工作。
- 一期先稳定基本 API 形态，二期可平滑扩展版本与字段。

### 1.2 设计约束
- 一期全部 HTTP 接口以 `/api/v1` 为根路径。
- 普通同步接口使用 `JSON`。
- 流式对话接口使用 `SSE`。
- 文件上传使用 `multipart/form-data`。
- 对外字段统一使用 `camelCase`。
- 数据库存储主键 `id` 不对前端暴露，优先暴露 `publicId`、业务单号或公开标识。

### 1.3 合理工程假设
- 一期前端为 Web 单端，不单独设计 App 专属 API。
- 一期不引入 GraphQL。
- 一期管理后台与用户端共用后端 API 服务，但通过权限和路由分组区分。
- 一期对话流接口默认基于已登录用户，不开放匿名聊天。

---

## 2. API 分组与模块划分

## 2.1 一级模块分组

| 模块 | 路径前缀 | 主要消费者 | 说明 |
|---|---|---|---|
| 认证模块 | `/api/v1/auth` | 前端登录页 | 登录、注册、刷新令牌 |
| 当前用户模块 | `/api/v1/users/me` | 前端用户中心 | 个人信息与偏好 |
| 主数据查询模块 | `/api/v1/departments` `/api/v1/doctors` | 前端、Agent | 科室与医生查询 |
| 号源与预约模块 | `/api/v1/schedule-slots` `/api/v1/appointments` | 前端、Agent | 号源查询、预约、取消 |
| 对话模块 | `/api/v1/chat` | 前端对话页 | 会话、消息、流式响应 |
| 管理后台模块 | `/api/v1/admin/*` | 管理后台 | 知识库、审计、配置、追踪 |

## 2.2 Controller 划分建议

建议在后端按以下 Controller 划分：
- `AuthController`
- `CurrentUserController`
- `DepartmentController`
- `DoctorController`
- `ScheduleSlotController`
- `AppointmentController`
- `ConversationController`
- `ChatStreamController`
- `AdminKnowledgeController`
- `AdminConversationController`
- `AdminAuditController`
- `AdminAgentTraceController`
- `AdminSystemConfigController`

## 2.3 接口职责边界

### 认证模块
- 只负责认证、令牌颁发与失效
- 不负责用户资料详情聚合

### 用户模块
- 只负责当前登录用户的资料与偏好
- 不承载后台用户管理能力

### 主数据查询模块
- 只读接口
- 用于前端展示与 Agent 查询工具
- 不承载写操作

### 预约模块
- 所有带副作用的医疗业务动作统一进入此模块
- 预约与取消必须收口到业务服务层，不允许对话模块绕过

### 对话模块
- 管理会话生命周期、消息读取、流式输出
- 不直接承担预约库存事务

### 管理后台模块
- 只对管理员开放
- 包含知识库管理、审计查看、Trace 查看、配置查看

---

## 3. 通用接口规范

## 3.1 路径与命名规范

### 路径规范
- 使用名词，不在 URL 中使用动作词
- 集合资源：`/appointments`
- 单个资源：`/appointments/{appointmentId}`
- 子资源或动作：
  - `POST /appointments/{appointmentId}/cancel`
  - `GET /conversations/{conversationId}/messages`

### 命名规范
- Request DTO：`XxxRequest`
- Query DTO：`XxxQuery`
- Response DTO：`XxxResponse`
- Item VO：`XxxItem`
- Page VO：`PageResponse<XxxItem>`

## 3.2 HTTP 方法规范

| 方法 | 用途 | 示例 |
|---|---|---|
| `GET` | 查询 | 查询科室、医生、号源、消息 |
| `POST` | 创建或动作 | 注册、登录、预约、取消、流式对话 |
| `PUT` | 全量更新 | 当前用户资料覆盖更新 |
| `PATCH` | 局部更新 | 二期可扩展 |
| `DELETE` | 删除 | 一期原则上较少使用 |

## 3.3 HTTP 状态码规范

### 成功类
- `200 OK`：普通成功
- `201 Created`：资源创建成功
- `204 No Content`：成功但无返回体，后续可用于删除

### 客户端错误
- `400 Bad Request`：参数结构错误
- `401 Unauthorized`：未登录或 token 无效
- `403 Forbidden`：已登录但无权限
- `404 Not Found`：资源不存在
- `409 Conflict`：状态冲突、幂等冲突、库存冲突
- `422 Unprocessable Entity`：业务规则不满足

### 服务端错误
- `500 Internal Server Error`
- `503 Service Unavailable`

**建议**
- HTTP 状态码表达大类错误
- 业务细节通过统一业务错误码返回

## 3.4 统一响应格式

### 成功响应
```json
{
  "code": "SUCCESS",
  "message": "success",
  "data": {},
  "requestId": "req_20260422_xxx",
  "timestamp": "2026-04-22T11:30:00+08:00"
}
```

### 失败响应
```json
{
  "code": "APPOINTMENT_SLOT_FULL",
  "message": "当前号源已约满",
  "details": [
    {
      "field": "slotId",
      "reason": "slot_full"
    }
  ],
  "requestId": "req_20260422_xxx",
  "timestamp": "2026-04-22T11:30:00+08:00"
}
```

### 字段说明
- `code`：业务错误码或 `SUCCESS`
- `message`：面向客户端展示的短描述
- `data`：成功时返回业务对象
- `details`：失败时的字段级说明
- `requestId`：链路追踪字段
- `timestamp`：服务端时间

## 3.5 分页结构

一期查询列表统一采用偏移分页，后续会话消息和日志可平滑演进为游标分页。

### 分页请求参数
- `pageNo`：默认 `1`
- `pageSize`：默认 `20`，最大 `100`

### 分页响应结构
```json
{
  "code": "SUCCESS",
  "message": "success",
  "data": {
    "list": [],
    "pageNo": 1,
    "pageSize": 20,
    "total": 125,
    "totalPages": 7,
    "hasNext": true
  },
  "requestId": "req_xxx",
  "timestamp": "2026-04-22T11:30:00+08:00"
}
```

### 通用分页 VO 建议
```java
public class PageResponse<T> {
    private List<T> list;
    private long total;
    private int pageNo;
    private int pageSize;
    private long totalPages;
    private boolean hasNext;
}
```

---

## 4. 统一错误码设计

## 4.1 错误码分层
建议使用字符串错误码，避免不同模块数字段难维护。

| 分组 | 前缀 | 示例 |
|---|---|---|
| 通用错误 | `COMMON_` | `COMMON_INVALID_PARAM` |
| 认证错误 | `AUTH_` | `AUTH_TOKEN_EXPIRED` |
| 用户错误 | `USER_` | `USER_NOT_FOUND` |
| 主数据错误 | `MASTER_` | `MASTER_DOCTOR_NOT_FOUND` |
| 预约错误 | `APPOINTMENT_` | `APPOINTMENT_SLOT_FULL` |
| 会话错误 | `CHAT_` | `CHAT_CONVERSATION_NOT_FOUND` |
| 知识库错误 | `KB_` | `KB_DOCUMENT_PARSE_FAILED` |
| 管理后台错误 | `ADMIN_` | `ADMIN_PERMISSION_DENIED` |

## 4.2 推荐错误码清单

### 通用
- `COMMON_INVALID_PARAM`
- `COMMON_RESOURCE_NOT_FOUND`
- `COMMON_CONFLICT`
- `COMMON_INTERNAL_ERROR`

### 认证
- `AUTH_UNAUTHORIZED`
- `AUTH_TOKEN_INVALID`
- `AUTH_TOKEN_EXPIRED`
- `AUTH_REFRESH_TOKEN_INVALID`
- `AUTH_LOGIN_FAILED`

### 用户
- `USER_ALREADY_EXISTS`
- `USER_NOT_FOUND`
- `USER_STATUS_INVALID`

### 主数据
- `MASTER_DEPARTMENT_NOT_FOUND`
- `MASTER_DOCTOR_NOT_FOUND`
- `MASTER_SLOT_NOT_FOUND`

### 预约
- `APPOINTMENT_SLOT_FULL`
- `APPOINTMENT_SLOT_CLOSED`
- `APPOINTMENT_CREATE_DUPLICATED`
- `APPOINTMENT_NOT_FOUND`
- `APPOINTMENT_STATUS_INVALID`
- `APPOINTMENT_CANCEL_NOT_ALLOWED`
- `APPOINTMENT_NOT_OWNER`

### 对话
- `CHAT_CONVERSATION_NOT_FOUND`
- `CHAT_MESSAGE_EMPTY`
- `CHAT_STREAM_INTERRUPTED`
- `CHAT_TOO_MANY_REQUESTS`

### 知识库
- `KB_DOCUMENT_NOT_FOUND`
- `KB_DOCUMENT_UPLOAD_FAILED`
- `KB_DOCUMENT_PARSE_FAILED`
- `KB_DOCUMENT_REINDEX_FAILED`
- `KB_PERMISSION_DENIED`

### 管理后台
- `ADMIN_PERMISSION_DENIED`
- `ADMIN_CONFIG_INVALID`

## 4.3 HTTP 状态码与业务错误码映射建议

| HTTP 状态码 | 业务错误示例 |
|---|---|
| `400` | `COMMON_INVALID_PARAM` |
| `401` | `AUTH_UNAUTHORIZED`, `AUTH_TOKEN_EXPIRED` |
| `403` | `ADMIN_PERMISSION_DENIED`, `KB_PERMISSION_DENIED` |
| `404` | `APPOINTMENT_NOT_FOUND`, `CHAT_CONVERSATION_NOT_FOUND` |
| `409` | `APPOINTMENT_CREATE_DUPLICATED`, `COMMON_CONFLICT` |
| `422` | `APPOINTMENT_SLOT_FULL`, `APPOINTMENT_CANCEL_NOT_ALLOWED` |
| `500` | `COMMON_INTERNAL_ERROR` |

---

## 5. 认证与鉴权接口设计

## 5.1 认证约定
- 认证方式：`Bearer Token`
- Header：
```http
Authorization: Bearer <access_token>
```

- 除登录、注册、刷新令牌外，默认都需要登录
- 管理后台接口额外要求管理员角色

## 5.2 登录接口

### `POST /api/v1/auth/login`
**职责**
- 用户登录
- 签发 access token 与 refresh token

**Request**
```json
{
  "account": "13800138000",
  "password": "123456",
  "loginType": "PASSWORD"
}
```

**字段说明**
- `account`：手机号或邮箱
- `password`：密码
- `loginType`：一期固定 `PASSWORD`

**Response**
```json
{
  "code": "SUCCESS",
  "message": "success",
  "data": {
    "accessToken": "xxx",
    "refreshToken": "yyy",
    "expiresIn": 7200,
    "tokenType": "Bearer",
    "user": {
      "userId": "f68c1df4-xxxx",
      "displayName": "张三",
      "mobileMasked": "138****8000",
      "roles": ["USER"]
    }
  },
  "requestId": "req_xxx",
  "timestamp": "2026-04-22T11:30:00+08:00"
}
```

## 5.3 注册接口

### `POST /api/v1/auth/register`
**职责**
- 创建普通用户账号

**Request**
```json
{
  "mobile": "13800138000",
  "email": "demo@example.com",
  "password": "123456",
  "displayName": "张三"
}
```

**Response**
```json
{
  "code": "SUCCESS",
  "message": "success",
  "data": {
    "userId": "f68c1df4-xxxx",
    "displayName": "张三"
  },
  "requestId": "req_xxx",
  "timestamp": "2026-04-22T11:30:00+08:00"
}
```

## 5.4 刷新令牌接口

### `POST /api/v1/auth/refresh`
**Request**
```json
{
  "refreshToken": "xxx"
}
```

**Response**
```json
{
  "code": "SUCCESS",
  "message": "success",
  "data": {
    "accessToken": "new_access_token",
    "refreshToken": "new_refresh_token",
    "expiresIn": 7200,
    "tokenType": "Bearer"
  },
  "requestId": "req_xxx",
  "timestamp": "2026-04-22T11:30:00+08:00"
}
```

## 5.5 登出接口

### `POST /api/v1/auth/logout`
**职责**
- 使当前 refresh token 失效

**Request**
```json
{
  "refreshToken": "xxx"
}
```

**Response**
```json
{
  "code": "SUCCESS",
  "message": "success",
  "data": {
    "logout": true
  },
  "requestId": "req_xxx",
  "timestamp": "2026-04-22T11:30:00+08:00"
}
```

## 5.6 当前用户接口

### `GET /api/v1/users/me`
**Response**
```json
{
  "code": "SUCCESS",
  "message": "success",
  "data": {
    "userId": "f68c1df4-xxxx",
    "displayName": "张三",
    "mobileMasked": "138****8000",
    "emailMasked": "de***@example.com",
    "defaultHospitalCode": "MAIN",
    "defaultDepartment": {
      "departmentId": "dep_xxx",
      "name": "呼吸内科"
    },
    "roles": ["USER"]
  },
  "requestId": "req_xxx",
  "timestamp": "2026-04-22T11:30:00+08:00"
}
```

### `PUT /api/v1/users/me`
**Request**
```json
{
  "displayName": "张三",
  "defaultHospitalCode": "MAIN",
  "defaultDepartmentId": "dep_xxx"
}
```

---

## 6. 主数据查询接口设计

## 6.1 科室查询接口

### `GET /api/v1/departments`
**职责**
- 查询可用科室列表
- 支持下拉选择、导诊结果联动

**Query 参数**
- `keyword`：可选，模糊匹配
- `status`：可选，默认 `ACTIVE`
- `pageNo`
- `pageSize`

**Response Item**
```json
{
  "departmentId": "dep_xxx",
  "code": "RESPIRATORY",
  "name": "呼吸内科",
  "description": "主要处理呼吸系统问题",
  "tags": ["咳嗽", "呼吸", "肺部"],
  "status": "ACTIVE"
}
```

## 6.2 医生列表接口

### `GET /api/v1/doctors`
**职责**
- 根据科室、关键词查询医生列表

**Query 参数**
- `departmentId`
- `keyword`
- `status`
- `pageNo`
- `pageSize`

**Response Item**
```json
{
  "doctorId": "doc_xxx",
  "name": "李医生",
  "title": "主任医师",
  "specialty": "咳嗽、哮喘、慢阻肺",
  "department": {
    "departmentId": "dep_xxx",
    "name": "呼吸内科"
  },
  "status": "ACTIVE"
}
```

## 6.3 医生详情接口

### `GET /api/v1/doctors/{doctorId}`
**Response**
```json
{
  "code": "SUCCESS",
  "message": "success",
  "data": {
    "doctorId": "doc_xxx",
    "name": "李医生",
    "title": "主任医师",
    "specialty": "咳嗽、哮喘、慢阻肺",
    "intro": "从业 20 年...",
    "department": {
      "departmentId": "dep_xxx",
      "name": "呼吸内科"
    },
    "tags": ["慢阻肺", "哮喘"],
    "status": "ACTIVE"
  },
  "requestId": "req_xxx",
  "timestamp": "2026-04-22T11:30:00+08:00"
}
```

---

## 7. 号源与预约接口设计

## 7.1 号源查询接口

### `GET /api/v1/schedule-slots`
**职责**
- 查询可预约号源
- 前端列表页与 Agent 查询工具共用

**Query 参数**
- `departmentId`：可选
- `doctorId`：可选
- `scheduleDate`：必填，格式 `yyyy-MM-dd`
- `periodCode`：可选
- `status`：默认查询 `OPEN`
- `pageNo`
- `pageSize`

**Response Item**
```json
{
  "slotId": "slot_xxx",
  "scheduleDate": "2026-04-23",
  "periodCode": "AM",
  "startTime": "09:00:00",
  "endTime": "12:00:00",
  "fee": 20.00,
  "availableQuota": 5,
  "totalQuota": 20,
  "status": "OPEN",
  "department": {
    "departmentId": "dep_xxx",
    "name": "呼吸内科"
  },
  "doctor": {
    "doctorId": "doc_xxx",
    "name": "李医生",
    "title": "主任医师"
  }
}
```

## 7.2 创建预约接口

### `POST /api/v1/appointments`
**职责**
- 创建预约
- 核心副作用接口

**Headers**
- `Idempotency-Key: <uuid>` 推荐必传

**Request**
```json
{
  "slotId": "slot_xxx",
  "remark": "尽量安排上午"
}
```

**Response**
```json
{
  "code": "SUCCESS",
  "message": "success",
  "data": {
    "appointmentId": "appt_xxx",
    "appointmentNo": "APT202604220001",
    "status": "CONFIRMED",
    "bookedAt": "2026-04-22T11:30:00+08:00",
    "schedule": {
      "slotId": "slot_xxx",
      "scheduleDate": "2026-04-23",
      "periodCode": "AM"
    },
    "department": {
      "departmentId": "dep_xxx",
      "name": "呼吸内科"
    },
    "doctor": {
      "doctorId": "doc_xxx",
      "name": "李医生"
    }
  },
  "requestId": "req_xxx",
  "timestamp": "2026-04-22T11:30:00+08:00"
}
```

**错误码重点**
- `MASTER_SLOT_NOT_FOUND`
- `APPOINTMENT_SLOT_FULL`
- `APPOINTMENT_SLOT_CLOSED`
- `APPOINTMENT_CREATE_DUPLICATED`

## 7.3 取消预约接口

### `POST /api/v1/appointments/{appointmentId}/cancel`
**职责**
- 取消预约并触发号源回补

**Request**
```json
{
  "cancelReason": "临时有事"
}
```

**Response**
```json
{
  "code": "SUCCESS",
  "message": "success",
  "data": {
    "appointmentId": "appt_xxx",
    "status": "CANCELLED",
    "cancelledAt": "2026-04-22T11:40:00+08:00",
    "cancelReason": "临时有事"
  },
  "requestId": "req_xxx",
  "timestamp": "2026-04-22T11:40:00+08:00"
}
```

**错误码重点**
- `APPOINTMENT_NOT_FOUND`
- `APPOINTMENT_NOT_OWNER`
- `APPOINTMENT_STATUS_INVALID`
- `APPOINTMENT_CANCEL_NOT_ALLOWED`

## 7.4 我的预约列表接口

### `GET /api/v1/appointments`
**Query 参数**
- `status`
- `scheduleDateFrom`
- `scheduleDateTo`
- `pageNo`
- `pageSize`

**Response Item**
```json
{
  "appointmentId": "appt_xxx",
  "appointmentNo": "APT202604220001",
  "status": "CONFIRMED",
  "bookedAt": "2026-04-22T11:30:00+08:00",
  "scheduleDate": "2026-04-23",
  "periodCode": "AM",
  "departmentName": "呼吸内科",
  "doctorName": "李医生",
  "fee": 20.00
}
```

## 7.5 预约详情接口

### `GET /api/v1/appointments/{appointmentId}`
**职责**
- 查询单条预约详情
- 给前端详情页与 Agent 二次确认使用

---

## 8. 对话与会话接口设计

## 8.1 创建会话接口

### `POST /api/v1/chat/conversations`
**Request**
```json
{
  "title": "导诊咨询",
  "sceneType": "GENERAL"
}
```

**Response**
```json
{
  "code": "SUCCESS",
  "message": "success",
  "data": {
    "conversationId": "conv_xxx",
    "title": "导诊咨询",
    "sceneType": "GENERAL",
    "status": "ACTIVE"
  },
  "requestId": "req_xxx",
  "timestamp": "2026-04-22T11:30:00+08:00"
}
```

## 8.2 会话列表接口

### `GET /api/v1/chat/conversations`
**Query 参数**
- `pageNo`
- `pageSize`

**Response Item**
```json
{
  "conversationId": "conv_xxx",
  "title": "导诊咨询",
  "sceneType": "GENERAL",
  "status": "ACTIVE",
  "lastMessageAt": "2026-04-22T11:50:00+08:00"
}
```

## 8.3 消息列表接口

### `GET /api/v1/chat/conversations/{conversationId}/messages`
**Query 参数**
- `pageNo`
- `pageSize`

**Response Item**
```json
{
  "messageId": "msg_xxx",
  "role": "ASSISTANT",
  "messageType": "TEXT",
  "content": "建议优先考虑呼吸内科。",
  "citations": [
    {
      "index": 1,
      "title": "导诊说明"
    }
  ],
  "createdAt": "2026-04-22T11:51:00+08:00"
}
```

## 8.4 会话摘要接口

### `GET /api/v1/chat/conversations/{conversationId}/summary`
**职责**
- 获取当前压缩摘要
- 供前端调试或后台查看

**Response**
```json
{
  "code": "SUCCESS",
  "message": "success",
  "data": {
    "conversationId": "conv_xxx",
    "summaryText": "用户主要咨询呼吸内科和挂号时间。",
    "businessState": {
      "lastIntent": "APPOINTMENT_QUERY"
    }
  },
  "requestId": "req_xxx",
  "timestamp": "2026-04-22T11:30:00+08:00"
}
```

---

## 9. SSE 流式对话接口设计

## 9.1 接口定义

### `POST /api/v1/chat/conversations/{conversationId}/stream`
**职责**
- 向指定会话发送一条用户消息
- 服务端以 `SSE` 返回模型流式结果、引用、工具状态

**Content-Type**
- 请求：`application/json`
- 响应：`text/event-stream`

**Request**
```json
{
  "message": "我最近咳嗽，应该挂哪个科？",
  "clientMessageId": "cli_msg_001",
  "context": {
    "sceneType": "GENERAL"
  }
}
```

### 9.1.1 请求字段说明
- `message`：用户输入
- `clientMessageId`：前端本地消息 ID，用于幂等展示
- `context.sceneType`：场景提示，非强约束

## 9.2 SSE 事件约定

一期建议使用以下事件名：
- `message_start`
- `tool_status`
- `citation`
- `message_delta`
- `message_end`
- `error`

## 9.3 事件结构设计

### `message_start`
```text
event: message_start
data: {"messageId":"msg_srv_001","conversationId":"conv_xxx","traceId":"trace_xxx"}
```

### `tool_status`
```text
event: tool_status
data: {"tool":"queryScheduleSlots","stage":"start","message":"正在查询号源"}
```

### `citation`
```text
event: citation
data: {"index":1,"title":"挂号规则说明","sourceId":"doc_123","snippet":"普通门诊预约需提前一天"}
```

### `message_delta`
```text
event: message_delta
data: {"delta":"建议优先考虑呼吸内科。"}
```

### `message_end`
```text
event: message_end
data: {
  "messageId":"msg_srv_001",
  "finishReason":"stop",
  "usage":{"inputTokens":1200,"outputTokens":260},
  "status":"SUCCESS"
}
```

### `error`
```text
event: error
data: {"code":"CHAT_STREAM_INTERRUPTED","message":"对话生成中断，请稍后重试"}
```

## 9.4 前后端交互约定
- 前端收到 `message_start` 后创建 assistant 占位消息
- 收到 `message_delta` 后增量拼接正文
- 收到 `citation` 后更新引用区
- 收到 `tool_status` 后更新“处理中”状态 UI
- 收到 `message_end` 后结束打字效果并记录 token 使用
- 收到 `error` 后结束流并提示用户重试

## 9.5 中断接口

### `POST /api/v1/chat/conversations/{conversationId}/stop`
**职责**
- 用户手动中断当前生成

**Request**
```json
{
  "traceId": "trace_xxx"
}
```

**Response**
```json
{
  "code": "SUCCESS",
  "message": "success",
  "data": {
    "stopped": true
  },
  "requestId": "req_xxx",
  "timestamp": "2026-04-22T11:30:00+08:00"
}
```

---

## 10. 知识库与管理后台接口设计

## 10.1 文档上传接口

### `POST /api/v1/admin/knowledge/documents/upload`
**职责**
- 上传知识文档
- 创建文档记录与入库任务

**Request**
- `multipart/form-data`
- 表单字段：
  - `file`
  - `title`
  - `bizScope`
  - `departmentTags`
  - `doctorTags`

**Response**
```json
{
  "code": "SUCCESS",
  "message": "success",
  "data": {
    "documentId": "doc_xxx",
    "title": "挂号规则说明",
    "bizScope": "APPOINTMENT_RULE",
    "publishStatus": "DRAFT",
    "ingestStatus": "UPLOADED"
  },
  "requestId": "req_xxx",
  "timestamp": "2026-04-22T11:30:00+08:00"
}
```

## 10.2 文档列表接口

### `GET /api/v1/admin/knowledge/documents`
**Query 参数**
- `title`
- `bizScope`
- `publishStatus`
- `ingestStatus`
- `pageNo`
- `pageSize`

## 10.3 文档详情接口

### `GET /api/v1/admin/knowledge/documents/{documentId}`
**Response**
```json
{
  "code": "SUCCESS",
  "message": "success",
  "data": {
    "documentId": "doc_xxx",
    "title": "挂号规则说明",
    "bizScope": "APPOINTMENT_RULE",
    "publishStatus": "DRAFT",
    "ingestStatus": "INDEXED",
    "departmentTags": ["呼吸内科"],
    "doctorTags": [],
    "uploadedAt": "2026-04-22T11:30:00+08:00"
  },
  "requestId": "req_xxx",
  "timestamp": "2026-04-22T11:30:00+08:00"
}
```

## 10.4 文档重建接口

### `POST /api/v1/admin/knowledge/documents/{documentId}/rebuild`
**职责**
- 触发重新解析/切片/索引

**Request**
```json
{
  "rebuildMode": "FULL"
}
```

## 10.5 文档发布接口

### `POST /api/v1/admin/knowledge/documents/{documentId}/publish`
**Request**
```json
{
  "publishAction": "PUBLISH"
}
```

**说明**
- `publishAction` 可取 `PUBLISH` / `OFFLINE`

## 10.6 检索测试接口

### `POST /api/v1/admin/knowledge/search/test`
**职责**
- 后台验证检索质量

**Request**
```json
{
  "query": "挂号可以取消吗",
  "bizScope": "APPOINTMENT_RULE",
  "topK": 5
}
```

**Response**
```json
{
  "code": "SUCCESS",
  "message": "success",
  "data": {
    "query": "挂号可以取消吗",
    "items": [
      {
        "documentId": "doc_xxx",
        "title": "挂号规则说明",
        "score": 0.92,
        "snippet": "如需取消挂号..."
      }
    ]
  },
  "requestId": "req_xxx",
  "timestamp": "2026-04-22T11:30:00+08:00"
}
```

## 10.7 管理后台补充接口

### `GET /api/v1/admin/conversations`
- 查看用户会话列表

### `GET /api/v1/admin/audit-logs`
- 查看审计日志

### `GET /api/v1/admin/agent-traces`
- 查看 Agent 调用链路

### `GET /api/v1/admin/system-config`
- 获取系统配置

### `PUT /api/v1/admin/system-config`
- 更新系统配置

---

## 11. DTO / VO 设计建议

## 11.1 Request DTO 建议清单
- `LoginRequest`
- `RegisterRequest`
- `RefreshTokenRequest`
- `UpdateCurrentUserRequest`
- `DepartmentListQuery`
- `DoctorListQuery`
- `ScheduleSlotListQuery`
- `CreateAppointmentRequest`
- `CancelAppointmentRequest`
- `CreateConversationRequest`
- `ChatStreamRequest`
- `StopChatRequest`
- `UploadKnowledgeDocumentRequest`
- `RebuildKnowledgeDocumentRequest`
- `PublishKnowledgeDocumentRequest`
- `KnowledgeSearchTestRequest`

## 11.2 Response VO 建议清单
- `TokenLoginResponse`
- `CurrentUserResponse`
- `DepartmentItem`
- `DoctorItem`
- `DoctorDetailResponse`
- `ScheduleSlotItem`
- `AppointmentResponse`
- `AppointmentItem`
- `ConversationItem`
- `ChatMessageItem`
- `ConversationSummaryResponse`
- `KnowledgeDocumentItem`
- `KnowledgeDocumentDetailResponse`
- `KnowledgeSearchTestItem`

## 11.3 通用对象建议
- `ApiResponse<T>`
- `PageResponse<T>`
- `ErrorDetail`
- `IdNamePair`

---

## 12. 幂等性、版本管理与兼容性建议

## 12.1 幂等性建议

### 必须幂等保护的接口
- `POST /api/v1/appointments`
- `POST /api/v1/appointments/{appointmentId}/cancel`
- `POST /api/v1/chat/conversations/{conversationId}/stop`

### 推荐做法
- 前端生成 `Idempotency-Key`
- 网关或后端透传到服务层
- 后端结合 `user_id + idempotency_key` 做唯一约束

## 12.2 版本管理建议
- URL 版本管理：`/api/v1`
- 二期新增不兼容接口时进入 `/api/v2`
- 向后兼容的小字段新增直接在 `v1` 扩展

## 12.3 字段兼容策略
- 新增字段只追加，不随意删除旧字段
- 状态值新增需保证前端默认兜底显示
- `jsonb` 扩展字段不能替代正式契约字段

---

## 13. 与数据库设计的衔接

## 13.1 资源 ID 映射原则
- API 暴露 `publicId`、`appointmentNo` 等公开标识
- Service 层内部转换为数据库主键 `id`
- 这样可以保护内部顺序主键，同时方便前后端联调

## 13.2 接口与表的关系

| 接口 | 主要读写表 |
|---|---|
| 登录/注册 | `user_account`, `user_profile`, `refresh_token` |
| 科室/医生查询 | `department`, `doctor` |
| 号源查询 | `schedule_slot`, `doctor`, `department` |
| 创建预约 | `appointment`, `schedule_slot`, `appointment_operation_log`, `audit_log` |
| 取消预约 | `appointment`, `schedule_slot`, `appointment_operation_log`, `audit_log` |
| 会话创建/消息查询 | `conversation`, `chat_message`, `conversation_summary` |
| SSE 对话 | `conversation`, `chat_message`, `agent_trace`, `user_memory_profile` |
| 文档上传/重建 | `knowledge_document`, `knowledge_ingest_task`, `knowledge_chunk` |

## 13.3 Controller 到 Service 的建议映射
- `AppointmentController` -> `AppointmentCommandService` + `AppointmentQueryService`
- `ChatStreamController` -> `ChatApplicationService`
- `AdminKnowledgeController` -> `KnowledgeDocumentService` + `KnowledgeSearchService`

---

## 14. 与 Agent 设计的衔接

## 14.1 Agent Tool 与开放 API 的边界
一期建议：
- Web 前端调用公开 REST API
- Agent 内部不直接调用 HTTP 接口
- Agent 调用内部 Service，与 Controller 契约保持语义一致

原因：
- 减少不必要的进程内 HTTP 跳转
- 保证 Tool 调用与 API 联调语义一致

## 14.2 Tool 入参与 API DTO 对齐建议

| Tool 名称 | 内部入参 | 建议对齐 API DTO |
|---|---|---|
| `queryDepartments` | `DepartmentListQuery` | `GET /departments` |
| `queryDoctors` | `DoctorListQuery` | `GET /doctors` |
| `queryScheduleSlots` | `ScheduleSlotListQuery` | `GET /schedule-slots` |
| `bookAppointment` | `CreateAppointmentCommand` | `POST /appointments` |
| `cancelAppointment` | `CancelAppointmentCommand` | `POST /appointments/{id}/cancel` |
| `searchKnowledge` | `KnowledgeSearchCommand` | `POST /admin/knowledge/search/test` 的简化内部版 |

## 14.3 Agent 输出与 API 输出边界
- Agent 输出更偏“解释 + 组织后的自然语言”
- API 输出更偏“结构化事实”
- 若 Agent 需要业务事实，必须来自 Service 查询结果，而不是根据自然语言猜测

---

## 15. Controller、DTO、联调实施建议

## 15.1 Controller 编写建议
- Controller 只负责：
  - 参数接收
  - 基础校验
  - 用户上下文读取
  - 调用 Service
  - 统一响应包装

- Controller 不负责：
  - 库存扣减
  - 状态流转
  - Prompt 组装
  - 检索拼接

## 15.2 DTO 校验建议
后端建议使用注解校验：
- `@NotBlank`
- `@NotNull`
- `@Size`
- `@Pattern`
- `@FutureOrPresent`

例如：
- `scheduleDate` 必须是今天或未来日期
- `pageSize` 最大值 100
- `message` 长度上限建议 4000 字符

## 15.3 联调建议
- 先完成 Swagger / OpenAPI 文档导出
- 前端优先联调：
  - 登录
  - 科室/医生/号源查询
  - 预约/取消
  - 会话创建
  - SSE 对话
- 管理后台联调放在第二阶段

## 15.4 OpenAPI 组织建议
按 Tag 分组：
- `Auth`
- `CurrentUser`
- `Department`
- `Doctor`
- `ScheduleSlot`
- `Appointment`
- `Conversation`
- `ChatStream`
- `AdminKnowledge`
- `AdminAudit`
- `AdminAgentTrace`
- `AdminSystemConfig`

---

## 16. 一期建议优先落地的 API 集合

### P0 必须优先
- `POST /api/v1/auth/login`
- `GET /api/v1/users/me`
- `GET /api/v1/departments`
- `GET /api/v1/doctors`
- `GET /api/v1/schedule-slots`
- `POST /api/v1/appointments`
- `POST /api/v1/appointments/{appointmentId}/cancel`
- `GET /api/v1/appointments`
- `POST /api/v1/chat/conversations`
- `GET /api/v1/chat/conversations`
- `GET /api/v1/chat/conversations/{conversationId}/messages`
- `POST /api/v1/chat/conversations/{conversationId}/stream`

### P1 紧随其后
- `POST /api/v1/auth/register`
- `POST /api/v1/auth/refresh`
- `POST /api/v1/auth/logout`
- `GET /api/v1/doctors/{doctorId}`
- `GET /api/v1/appointments/{appointmentId}`
- `GET /api/v1/chat/conversations/{conversationId}/summary`
- 管理后台知识库接口

### P2 预留即可
- `POST /api/v1/chat/conversations/{conversationId}/stop`
- 管理后台追踪与配置接口

---

## 17. 本文结论
- 一期 API 契约应围绕“用户端业务闭环 + 对话流 + 后台知识管理”三条主线组织。
- 所有带副作用的业务动作必须进入业务服务层，接口层不直接承载规则。
- 统一响应结构、错误码、分页、SSE 事件协议可以直接成为 Controller 和前端联调基线。
- API 对外使用公开资源标识，与数据库内部主键解耦。
- Agent 与公开 API 保持语义一致，但内部优先走 Service 而非 HTTP。
- 按本文细化后，下一步可以继续输出：
  - `04-service-application-design.md`
  - 或先补 `OpenAPI 草案`
  - 或直接开始搭建 Controller/DTO 骨架代码

