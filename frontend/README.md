# Frontend

这是 Medora 的 Next.js 15 / React 19 前端，包含患者端、管理后台与 A2UI 预约交互。

## 环境

- Node.js 22（见 `.nvmrc`）
- npm（使用已提交的 `package-lock.json`）
- 已启动的 Medora 后端

## 安装与启动

```bash
npm ci
npm run build
npm run dev
```

默认访问 `http://localhost:3000`，默认后端地址为 `http://localhost:8080/api/v1`。如需覆盖，在项目根 `.env` 中设置：

```dotenv
NEXT_PUBLIC_API_BASE_URL=http://localhost:8080/api/v1
NEXT_PUBLIC_APP_NAME=Medora
```

不要在 `NEXT_PUBLIC_*` 变量中放置 API key、JWT secret 或其他敏感值；这些变量会进入浏览器构建产物。
