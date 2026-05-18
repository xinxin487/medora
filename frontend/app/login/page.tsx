"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import type { FormEvent } from "react";
import { PatientShell } from "@/components/patient-shell";
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

const accountBenefits = [
  "保存登录态和个人默认偏好",
  "在预约中心快速复用上次选择",
  "让导诊助手和预约服务共享同一用户身份",
];

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
  const [message, setMessage] = useState("欢迎来到 Medora 账户中心。");
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
      setMessage(`注册成功：${result.displayName}。你现在可以直接登录。`);
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
      setMessage(`欢迎回来，${result.user.displayName}。账户已接入预约和导诊服务。`);
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
      setMessage(`当前账户：${result.displayName}`);
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
      setMessage(`个人资料已更新：${result.displayName}`);
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
      setMessage("你已退出账户，本地令牌也已清除。");
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
    <PatientShell
      eyebrow="Account Center"
      title="登录、注册和账户偏好，在同一页里组织成真正可用的账户体验。"
      description="Medora 的账户页不只是为了拿 token。它承担用户身份、个人偏好、默认就诊设置和后续预约/导诊连续性的入口作用。"
      actions={
        <>
          <Link
            href="/appointments"
            className="rounded-full bg-[var(--ink-strong)] px-5 py-3 text-sm font-semibold text-white"
          >
            去预约中心
          </Link>
          <Link
            href="/chat"
            className="rounded-full border border-[var(--accent-main)] bg-[rgba(31,92,82,0.08)] px-5 py-3 text-sm font-semibold text-[var(--accent-main)]"
          >
            去导诊助手
          </Link>
        </>
      }
    >
      <div className="grid gap-5 lg:grid-cols-[0.92fr_1.08fr]">
        <aside className="space-y-5">
          <div className="medora-surface border border-[var(--line-soft)] px-5 py-6">
            <p className="text-xs font-semibold uppercase tracking-[0.24em] text-[var(--accent-main)]">
              Account Status
            </p>
            <p className="mt-4 text-sm leading-7 text-[var(--ink-body)]">{message}</p>
            {error ? (
              <div className="mt-4 rounded-[1.2rem] border border-[rgba(190,92,67,0.18)] bg-[var(--danger-soft)] px-4 py-3 text-sm leading-6 text-[#8a4a3a]">
                {error}
              </div>
            ) : null}

            <div className="mt-5 grid gap-3 sm:grid-cols-2">
              <StatusCard label="Access Token" value={tokenState.hasAccessToken ? "已写入" : "未写入"} />
              <StatusCard label="Refresh Token" value={tokenState.hasRefreshToken ? "已写入" : "未写入"} />
              <StatusCard label="当前账户" value={currentUser ? currentUser.displayName : "未登录"} />
              <StatusCard label="角色" value={currentUser ? currentUser.roles.join(", ") : "访客"} />
            </div>
          </div>

          <div className="medora-surface border border-[var(--line-soft)] bg-[linear-gradient(180deg,rgba(237,243,238,0.92),rgba(255,255,255,0.9))] px-5 py-6">
            <p className="text-xs font-semibold uppercase tracking-[0.24em] text-[var(--ink-subtle)]">
              Why Sign In
            </p>
            <h2 className="medora-display mt-4 text-3xl font-semibold tracking-[-0.04em]">
              账户不是障碍，而是让服务连续起来的锚点。
            </h2>
            <ul className="mt-5 space-y-3">
              {accountBenefits.map((item) => (
                <li
                  key={item}
                  className="rounded-[1.2rem] border border-[rgba(20,42,39,0.08)] bg-white/72 px-4 py-4 text-sm leading-7 text-[var(--ink-body)]"
                >
                  {item}
                </li>
              ))}
            </ul>
          </div>
        </aside>

        <section className="space-y-5">
          {currentUser ? (
            <div className="grid gap-5 xl:grid-cols-[0.92fr_1.08fr]">
              <div className="medora-surface border border-[var(--line-soft)] px-5 py-6">
                <div className="flex flex-wrap items-start justify-between gap-4">
                  <div>
                    <p className="text-xs font-semibold uppercase tracking-[0.24em] text-[var(--accent-main)]">
                      Logged In
                    </p>
                    <h2 className="mt-4 text-2xl font-semibold">{currentUser.displayName}</h2>
                    <p className="mt-2 text-sm leading-7 text-[var(--ink-body)]">
                      {currentUser.emailMasked ?? currentUser.mobileMasked ?? "暂无联系方式"} ·{" "}
                      {currentUser.roles.join(", ")}
                    </p>
                  </div>
                  <button
                    className="rounded-full border border-[var(--line-soft)] px-4 py-2 text-sm font-semibold text-[var(--ink-body)] transition hover:border-[var(--accent-main)]"
                    disabled={isBusy}
                    onClick={() => void loadCurrentUser()}
                    type="button"
                  >
                    刷新资料
                  </button>
                </div>

                <div className="mt-5 grid gap-3 md:grid-cols-2">
                  <MetricTile label="默认院区" value={currentUser.defaultHospitalCode ?? "MAIN"} />
                  <MetricTile
                    label="默认科室"
                    value={currentUser.defaultDepartment?.name ?? "尚未设置"}
                  />
                </div>

                <div className="mt-5 flex flex-wrap gap-3">
                  <Link
                    href="/appointments"
                    className="rounded-full bg-[var(--ink-strong)] px-5 py-3 text-sm font-semibold text-white"
                  >
                    去预约中心
                  </Link>
                  <Link
                    href="/chat"
                    className="rounded-full border border-[var(--accent-main)] bg-[rgba(31,92,82,0.08)] px-5 py-3 text-sm font-semibold text-[var(--accent-main)]"
                  >
                    去导诊助手
                  </Link>
                  <button
                    className="rounded-full border border-[rgba(190,92,67,0.22)] bg-[var(--danger-soft)] px-5 py-3 text-sm font-semibold text-[#8a4a3a]"
                    disabled={isBusy}
                    onClick={() => void handleLogout()}
                    type="button"
                  >
                    退出登录
                  </button>
                </div>
              </div>

              <form className="medora-surface border border-[var(--line-soft)] px-5 py-6" onSubmit={handleProfileUpdate}>
                <p className="text-xs font-semibold uppercase tracking-[0.24em] text-[var(--ink-subtle)]">
                  Preferences
                </p>
                <h2 className="mt-4 text-xl font-semibold">账户偏好</h2>
                <div className="mt-5 space-y-4">
                  <Field
                    label="显示名称"
                    value={profileForm.displayName}
                    onChange={(value) => setProfileForm((prev) => ({ ...prev, displayName: value }))}
                  />
                  <Field
                    label="默认院区编码"
                    value={profileForm.defaultHospitalCode}
                    onChange={(value) =>
                      setProfileForm((prev) => ({ ...prev, defaultHospitalCode: value }))
                    }
                  />
                  <Field
                    label="默认科室 ID"
                    value={profileForm.defaultDepartmentId}
                    onChange={(value) =>
                      setProfileForm((prev) => ({ ...prev, defaultDepartmentId: value }))
                    }
                  />
                </div>
                <button
                  className="mt-5 rounded-full bg-[var(--accent-main)] px-5 py-3 text-sm font-semibold text-white disabled:opacity-60"
                  disabled={isBusy}
                  type="submit"
                >
                  保存偏好
                </button>
              </form>
            </div>
          ) : (
            <div className="grid gap-5 xl:grid-cols-2">
              <form className="medora-surface border border-[var(--line-soft)] px-5 py-6" onSubmit={handleLogin}>
                <p className="text-xs font-semibold uppercase tracking-[0.24em] text-[var(--accent-main)]">
                  Sign In
                </p>
                <h2 className="mt-4 text-2xl font-semibold">返回你的就诊路径</h2>
                <div className="mt-5 space-y-4">
                  <Field
                    label="手机号或邮箱"
                    value={loginForm.account}
                    onChange={(value) => setLoginForm((prev) => ({ ...prev, account: value }))}
                  />
                  <Field
                    label="密码"
                    type="password"
                    value={loginForm.password}
                    onChange={(value) => setLoginForm((prev) => ({ ...prev, password: value }))}
                  />
                </div>
                <button
                  className="mt-5 rounded-full bg-[var(--ink-strong)] px-5 py-3 text-sm font-semibold text-white disabled:opacity-60"
                  disabled={isBusy}
                  type="submit"
                >
                  登录并继续
                </button>
              </form>

              <form className="medora-surface border border-[var(--line-soft)] px-5 py-6" onSubmit={handleRegister}>
                <p className="text-xs font-semibold uppercase tracking-[0.24em] text-[var(--accent-warm)]">
                  Create Account
                </p>
                <h2 className="mt-4 text-2xl font-semibold">创建一个新的 Medora 账户</h2>
                <div className="mt-5 space-y-4">
                  <Field
                    label="显示名称"
                    value={registerForm.displayName}
                    onChange={(value) => setRegisterForm((prev) => ({ ...prev, displayName: value }))}
                  />
                  <Field
                    label="手机号"
                    value={registerForm.mobile}
                    onChange={(value) => setRegisterForm((prev) => ({ ...prev, mobile: value }))}
                  />
                  <Field
                    label="邮箱"
                    value={registerForm.email}
                    onChange={(value) => setRegisterForm((prev) => ({ ...prev, email: value }))}
                  />
                  <Field
                    label="密码"
                    type="password"
                    value={registerForm.password}
                    onChange={(value) => setRegisterForm((prev) => ({ ...prev, password: value }))}
                  />
                </div>
                <button
                  className="mt-5 rounded-full bg-[var(--accent-main)] px-5 py-3 text-sm font-semibold text-white disabled:opacity-60"
                  disabled={isBusy}
                  type="submit"
                >
                  创建账户
                </button>
              </form>
            </div>
          )}
        </section>
      </div>
    </PatientShell>
  );
}

function Field({
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
    <label className="block">
      <span className="text-sm font-semibold text-[var(--ink-body)]">{label}</span>
      <input
        className="mt-2 w-full rounded-[1.1rem] border border-[var(--line-soft)] bg-white/80 px-4 py-3 text-sm text-[var(--ink-strong)] outline-none transition focus:border-[var(--accent-main)]"
        type={type}
        value={value}
        onChange={(event) => onChange(event.target.value)}
      />
    </label>
  );
}

function StatusCard({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-[1.2rem] border border-[rgba(20,42,39,0.08)] bg-white/72 px-4 py-4">
      <p className="text-xs font-semibold uppercase tracking-[0.2em] text-[var(--ink-subtle)]">{label}</p>
      <p className="mt-3 text-base font-semibold text-[var(--ink-strong)]">{value}</p>
    </div>
  );
}

function MetricTile({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-[1.2rem] border border-[rgba(20,42,39,0.08)] bg-[rgba(237,243,238,0.56)] px-4 py-4">
      <p className="text-xs font-semibold uppercase tracking-[0.2em] text-[var(--ink-subtle)]">{label}</p>
      <p className="mt-3 text-base font-semibold text-[var(--ink-strong)]">{value}</p>
    </div>
  );
}
