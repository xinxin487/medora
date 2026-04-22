"use client";

import { useEffect, useState } from "react";
import type { FormEvent, ReactNode } from "react";
import { ApiClientError, apiFetch } from "@/lib/api/client";
import { clearTokens, getAccessToken, getRefreshToken, setTokens } from "@/lib/auth/token";

type LoggedInUser = {
  userId: string;
  displayName: string;
  mobileMasked: string | null;
  roles: string[];
};

type LoginResult = {
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
  tokenType: string;
  user: LoggedInUser;
};

type RegisterResult = {
  userId: string;
  displayName: string;
};

type CurrentUser = {
  userId: string;
  displayName: string;
  mobileMasked: string | null;
  emailMasked: string | null;
  defaultHospitalCode: string | null;
  defaultDepartment: {
    departmentId: string;
    name: string;
  } | null;
  roles: string[];
};

export default function LoginPage() {
  const [loginForm, setLoginForm] = useState({
    account: "demo@example.com",
    password: "123456",
    loginType: "PASSWORD",
  });
  const [registerForm, setRegisterForm] = useState({
    mobile: "13800138000",
    email: "demo@example.com",
    password: "123456",
    displayName: "张三",
  });
  const [profileForm, setProfileForm] = useState({
    displayName: "",
    defaultHospitalCode: "MAIN",
    defaultDepartmentId: "",
  });
  const [tokenState, setTokenState] = useState({
    hasAccessToken: false,
    hasRefreshToken: false,
  });
  const [currentUser, setCurrentUser] = useState<CurrentUser | null>(null);
  const [message, setMessage] = useState("准备联调 Phase 2 认证链路。");
  const [error, setError] = useState<string | null>(null);
  const [isBusy, setIsBusy] = useState(false);

  useEffect(() => {
    syncTokenState();
    if (getAccessToken()) {
      void loadCurrentUser();
    }
  }, []);

  async function handleRegister(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    await runAction(async () => {
      const result = await apiFetch<RegisterResult>(
        "/auth/register",
        {
          method: "POST",
          body: JSON.stringify(registerForm),
        },
        { withAuth: false },
      );
      setMessage(`注册成功：${result.displayName}，现在可以直接登录。`);
      setLoginForm((prev) => ({
        ...prev,
        account: registerForm.email || registerForm.mobile,
        password: registerForm.password,
      }));
    });
  }

  async function handleLogin(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    await runAction(async () => {
      const result = await apiFetch<LoginResult>(
        "/auth/login",
        {
          method: "POST",
          body: JSON.stringify(loginForm),
        },
        { withAuth: false },
      );
      setTokens(result.accessToken, result.refreshToken);
      syncTokenState();
      setMessage(`登录成功：${result.user.displayName}，Token 已写入本地存储。`);
      await loadCurrentUser();
    });
  }

  async function loadCurrentUser() {
    await runAction(async () => {
      const result = await apiFetch<CurrentUser>("/users/me");
      setCurrentUser(result);
      setProfileForm({
        displayName: result.displayName,
        defaultHospitalCode: result.defaultHospitalCode ?? "MAIN",
        defaultDepartmentId: result.defaultDepartment?.departmentId ?? "",
      });
      setMessage(`已读取当前用户：${result.displayName}`);
    }, false);
  }

  async function handleProfileUpdate(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    await runAction(async () => {
      const result = await apiFetch<CurrentUser>("/users/me", {
        method: "PUT",
        body: JSON.stringify(profileForm),
      });
      setCurrentUser(result);
      setMessage(`资料更新成功：${result.displayName}`);
    });
  }

  async function handleLogout() {
    await runAction(async () => {
      const refreshToken = getRefreshToken();
      if (refreshToken) {
        await apiFetch<{ logout: boolean }>(
          "/auth/logout",
          {
            method: "POST",
            body: JSON.stringify({ refreshToken }),
          },
          { withAuth: false },
        );
      }
      clearTokens();
      syncTokenState();
      setCurrentUser(null);
      setMessage("已清除本地 token，并请求后端注销 refresh token。");
    });
  }

  function syncTokenState() {
    setTokenState({
      hasAccessToken: Boolean(getAccessToken()),
      hasRefreshToken: Boolean(getRefreshToken()),
    });
  }

  async function runAction(action: () => Promise<void>, resetError = true) {
    setIsBusy(true);
    if (resetError) {
      setError(null);
    }
    try {
      await action();
    } catch (cause) {
      if (cause instanceof ApiClientError) {
        const detailText =
          cause.details.length > 0
            ? ` ${cause.details.map((item) => `${item.field}: ${item.reason}`).join(" | ")}`
            : "";
        setError(`${cause.code}: ${cause.message}${detailText}`);
      } else {
        setError("请求失败，请检查后端服务是否已启动。");
      }
    } finally {
      setIsBusy(false);
    }
  }

  return (
    <main className="min-h-screen px-6 py-10">
      <section className="mx-auto grid max-w-7xl gap-6 lg:grid-cols-[0.95fr_1.05fr]">
        <div className="rounded-[2rem] border border-white/70 bg-white/75 p-8 shadow-[0_24px_70px_rgba(15,23,42,0.08)] backdrop-blur">
          <p className="text-xs font-semibold uppercase tracking-[0.26em] text-emerald-700">
            Phase 2 Workflow
          </p>
          <h1 className="mt-4 text-4xl font-semibold leading-tight text-slate-900">
            注册、登录、读取当前用户和资料更新，都可以在这一页直接联调。
          </h1>
          <p className="mt-5 text-base leading-8 text-slate-600">
            这页优先验证接口链路是否跑通，而不是做成最终用户界面。Phase 2 验证通过后，我们就能继续接预约闭环。
          </p>

          <div className="mt-8 rounded-[1.75rem] border border-emerald-200 bg-emerald-50 p-5 text-sm leading-7 text-emerald-950">
            <p className="font-semibold">当前状态</p>
            <p className="mt-2">{message}</p>
            {error ? (
              <p className="mt-3 rounded-2xl border border-rose-200 bg-rose-50 px-4 py-3 text-rose-700">
                {error}
              </p>
            ) : null}
          </div>

          <div className="mt-8 grid gap-4 sm:grid-cols-2">
            <StatusCard
              label="Access Token"
              value={tokenState.hasAccessToken ? "已存在" : "未写入"}
            />
            <StatusCard
              label="Refresh Token"
              value={tokenState.hasRefreshToken ? "已存在" : "未写入"}
            />
            <StatusCard label="当前用户" value={currentUser ? currentUser.displayName : "未登录"} />
            <StatusCard label="角色" value={currentUser ? currentUser.roles.join(", ") : "无"} />
          </div>
        </div>

        <div className="grid gap-6">
          <Panel title="注册" subtitle="创建普通用户账号，用于验证注册链路。">
            <form className="grid gap-4" onSubmit={handleRegister}>
              <Input
                label="手机号"
                value={registerForm.mobile}
                onChange={(value) => setRegisterForm((prev) => ({ ...prev, mobile: value }))}
              />
              <Input
                label="邮箱"
                value={registerForm.email}
                onChange={(value) => setRegisterForm((prev) => ({ ...prev, email: value }))}
              />
              <Input
                label="密码"
                type="password"
                value={registerForm.password}
                onChange={(value) => setRegisterForm((prev) => ({ ...prev, password: value }))}
              />
              <Input
                label="显示名"
                value={registerForm.displayName}
                onChange={(value) => setRegisterForm((prev) => ({ ...prev, displayName: value }))}
              />
              <SubmitButton label="提交注册" busy={isBusy} />
            </form>
          </Panel>

          <div className="grid gap-6 xl:grid-cols-2">
            <Panel title="登录" subtitle="写入 Access Token 和 Refresh Token。">
              <form className="grid gap-4" onSubmit={handleLogin}>
                <Input
                  label="账号"
                  value={loginForm.account}
                  onChange={(value) => setLoginForm((prev) => ({ ...prev, account: value }))}
                />
                <Input
                  label="密码"
                  type="password"
                  value={loginForm.password}
                  onChange={(value) => setLoginForm((prev) => ({ ...prev, password: value }))}
                />
                <SubmitButton label="执行登录" busy={isBusy} />
              </form>
            </Panel>

            <Panel title="当前用户" subtitle="读取和更新 `/api/v1/users/me`。">
              <div className="flex gap-3">
                <button
                  type="button"
                  onClick={() => void loadCurrentUser()}
                  className="rounded-full border border-slate-200 px-4 py-2 text-sm font-semibold text-slate-700 transition hover:border-slate-400"
                >
                  刷新资料
                </button>
                <button
                  type="button"
                  onClick={() => void handleLogout()}
                  className="rounded-full border border-rose-200 px-4 py-2 text-sm font-semibold text-rose-700 transition hover:border-rose-400"
                >
                  退出登录
                </button>
              </div>

              <form className="mt-5 grid gap-4" onSubmit={handleProfileUpdate}>
                <Input
                  label="显示名"
                  value={profileForm.displayName}
                  onChange={(value) => setProfileForm((prev) => ({ ...prev, displayName: value }))}
                />
                <Input
                  label="默认院区"
                  value={profileForm.defaultHospitalCode}
                  onChange={(value) =>
                    setProfileForm((prev) => ({ ...prev, defaultHospitalCode: value }))
                  }
                />
                <Input
                  label="默认科室 ID"
                  value={profileForm.defaultDepartmentId}
                  onChange={(value) =>
                    setProfileForm((prev) => ({ ...prev, defaultDepartmentId: value }))
                  }
                />
                <SubmitButton label="更新资料" busy={isBusy} />
              </form>
            </Panel>
          </div>
        </div>
      </section>
    </main>
  );
}

function Panel({
  title,
  subtitle,
  children,
}: {
  title: string;
  subtitle: string;
  children: ReactNode;
}) {
  return (
    <section className="rounded-[2rem] border border-white/70 bg-white/80 p-6 shadow-[0_20px_60px_rgba(15,23,42,0.08)] backdrop-blur">
      <h2 className="text-2xl font-semibold text-slate-900">{title}</h2>
      <p className="mt-2 text-sm leading-7 text-slate-500">{subtitle}</p>
      <div className="mt-5">{children}</div>
    </section>
  );
}

function Input({
  label,
  value,
  onChange,
  type = "text",
}: {
  label: string;
  value: string;
  onChange: (value: string) => void;
  type?: string;
}) {
  return (
    <label className="grid gap-2 text-sm font-medium text-slate-700">
      <span>{label}</span>
      <input
        type={type}
        value={value}
        onChange={(event) => onChange(event.target.value)}
        className="rounded-2xl border border-slate-200 bg-slate-50 px-4 py-3 text-sm text-slate-900 outline-none transition focus:border-emerald-400 focus:bg-white"
      />
    </label>
  );
}

function SubmitButton({ label, busy }: { label: string; busy: boolean }) {
  return (
    <button
      type="submit"
      disabled={busy}
      className="rounded-full bg-slate-900 px-5 py-3 text-sm font-semibold text-white transition hover:bg-slate-700 disabled:cursor-not-allowed disabled:bg-slate-400"
    >
      {busy ? "处理中..." : label}
    </button>
  );
}

function StatusCard({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-[1.4rem] border border-slate-200 bg-slate-50 px-4 py-4">
      <p className="text-xs font-semibold uppercase tracking-[0.18em] text-slate-500">
        {label}
      </p>
      <p className="mt-2 text-lg font-semibold text-slate-900">{value}</p>
    </div>
  );
}
