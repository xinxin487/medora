"use client";

import { useState } from "react";
import type { FormEvent } from "react";
import { useRouter } from "next/navigation";
import { apiFetch } from "@/lib/api/client";
import { setTokens } from "@/lib/auth/token";
import { useAsyncAction } from "@/lib/hooks/use-async-action";
import type { LoginResult, RegisterResult } from "@/lib/types/api";
import { Badge } from "@/components/badge";

const highlights = [
  { title: "连续导诊", body: "保留近期会话、引用与就诊方向。" },
  { title: "快速预约", body: "从科室筛选到锁定号源一页完成。" },
  { title: "统一后台", body: "知识库、Trace 与审计集中管理。" },
];

export default function LoginPage() {
  const router = useRouter();
  const { isBusy, error, runAction } = useAsyncAction();
  const [mode, setMode] = useState<"login" | "register">("login");
  const [successMsg, setSuccessMsg] = useState<string | null>(null);

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

  async function handleLogin(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    await runAction(async () => {
      const result = await apiFetch<LoginResult>(
        "/auth/login",
        { method: "POST", body: JSON.stringify(loginForm) },
        { withAuth: false },
      );
      setTokens(result.accessToken, result.refreshToken);

      if (result.user.roles.includes("ADMIN")) {
        router.replace("/admin");
      } else {
        router.replace("/app/dashboard");
      }
    });
  }

  async function handleRegister(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    await runAction(async () => {
      const result = await apiFetch<RegisterResult>(
        "/auth/register",
        { method: "POST", body: JSON.stringify(registerForm) },
        { withAuth: false },
      );
      setSuccessMsg(`注册成功：${result.displayName}。请使用新账号登录。`);
      setLoginForm((prev) => ({
        ...prev,
        account: registerForm.email || registerForm.mobile,
        password: registerForm.password,
      }));
      setMode("login");
    });
  }

  return (
    <main className="min-h-screen px-4 py-6 sm:px-6">
      <div className="mx-auto max-w-7xl">
        <div className="grid gap-6 xl:grid-cols-[1.08fr_0.92fr]">
          {/* ── Left: Hero Panel ── */}
          <section className="overflow-hidden rounded-[2.4rem] bg-[linear-gradient(135deg,#143b35_0%,#1f5c52_52%,#2c776c_100%)] p-7 text-white shadow-[0_30px_90px_rgba(20,42,39,0.24)] sm:p-10">
            <div className="flex items-center justify-between gap-3">
              <div>
                <div className="text-xs font-semibold uppercase tracking-[0.28em] text-white/60">
                  Medora
                </div>
                <div className="mt-2 text-sm font-semibold text-white/82">
                  Medical Access Platform
                </div>
              </div>
              <span className="rounded-full border border-white/14 bg-white/10 px-4 py-2 text-sm font-semibold text-white/80">
                安全入口
              </span>
            </div>

            <div className="mt-14 max-w-3xl">
              <h1 className="medora-display text-5xl leading-[1.06] text-white sm:text-[4.5rem]">
                把导诊、挂号与连续服务放进同一个入口。
              </h1>
              <p className="mt-6 max-w-2xl text-base leading-8 text-white/76">
                登录后自动进入对应工作区，继续你的预约、导诊和后台运营任务。
              </p>
            </div>

            <div className="mt-10 grid gap-4 md:grid-cols-3">
              {highlights.map((item) => (
                <div
                  key={item.title}
                  className="rounded-[1.7rem] border border-white/12 bg-white/8 p-5 backdrop-blur"
                >
                  <p className="text-lg font-semibold">{item.title}</p>
                  <p className="mt-3 text-sm leading-6 text-white/70">
                    {item.body}
                  </p>
                </div>
              ))}
            </div>
          </section>

          {/* ── Right: Form Panel ── */}
          <section className="medora-surface medora-grid border border-white/60 p-6 sm:p-8">
            <div className="flex items-center justify-between gap-3">
              <div>
                <div className="text-xs font-semibold uppercase tracking-[0.24em] text-[var(--accent-main)]">
                  {mode === "login" ? "Unified Sign In" : "Create Account"}
                </div>
                <h2 className="mt-3 text-3xl font-semibold text-[var(--ink-strong)]">
                  {mode === "login" ? "欢迎回来" : "创建新账户"}
                </h2>
              </div>
              <Badge tone="soft">自动识别角色</Badge>
            </div>

            {error ? (
              <div className="mt-6 rounded-[1.2rem] border border-[rgba(190,92,67,0.18)] bg-[var(--danger-soft)] px-4 py-3 text-sm leading-6 text-[#8a4a3a]">
                {error}
              </div>
            ) : null}

            {successMsg ? (
              <div className="mt-6 rounded-[1.2rem] border border-[rgba(31,92,82,0.18)] bg-[var(--bg-soft)] px-4 py-3 text-sm leading-6 text-[var(--accent-main-strong)]">
                {successMsg}
              </div>
            ) : null}

            {mode === "login" ? (
              <form onSubmit={handleLogin}>
                <div className="mt-8 space-y-4">
                  <Field
                    label="手机号或邮箱"
                    value={loginForm.account}
                    onChange={(v) =>
                      setLoginForm((prev) => ({ ...prev, account: v }))
                    }
                  />
                  <Field
                    label="密码"
                    type="password"
                    value={loginForm.password}
                    onChange={(v) =>
                      setLoginForm((prev) => ({ ...prev, password: v }))
                    }
                  />
                </div>

                <div className="mt-4 flex flex-wrap items-center justify-between gap-3">
                  <div className="flex flex-wrap gap-2">
                    <Badge>安全登录</Badge>
                  </div>
                </div>

                <button
                  className="mt-6 block w-full rounded-[1.35rem] bg-[var(--accent-main)] px-4 py-3.5 text-center text-sm font-semibold text-white shadow-[0_18px_44px_rgba(31,92,82,0.22)] transition hover:bg-[var(--accent-main-strong)] disabled:opacity-60"
                  disabled={isBusy}
                  type="submit"
                >
                  {isBusy ? "登录中..." : "登录并进入工作区"}
                </button>

                <button
                  type="button"
                  className="mt-3 block w-full rounded-[1.35rem] border border-[var(--line-soft)] bg-white px-4 py-3.5 text-center text-sm font-semibold text-[var(--ink-strong)] transition hover:border-[var(--accent-main)] hover:text-[var(--accent-main)]"
                  onClick={() => {
                    setMode("register");
                    setSuccessMsg(null);
                  }}
                >
                  没有账户？注册一个
                </button>
              </form>
            ) : (
              <form onSubmit={handleRegister}>
                <div className="mt-8 space-y-4">
                  <Field
                    label="显示名称"
                    value={registerForm.displayName}
                    onChange={(v) =>
                      setRegisterForm((prev) => ({ ...prev, displayName: v }))
                    }
                  />
                  <Field
                    label="手机号"
                    value={registerForm.mobile}
                    onChange={(v) =>
                      setRegisterForm((prev) => ({ ...prev, mobile: v }))
                    }
                  />
                  <Field
                    label="邮箱"
                    value={registerForm.email}
                    onChange={(v) =>
                      setRegisterForm((prev) => ({ ...prev, email: v }))
                    }
                  />
                  <Field
                    label="密码"
                    type="password"
                    value={registerForm.password}
                    onChange={(v) =>
                      setRegisterForm((prev) => ({ ...prev, password: v }))
                    }
                  />
                </div>

                <button
                  className="mt-6 block w-full rounded-[1.35rem] bg-[var(--accent-main)] px-4 py-3.5 text-center text-sm font-semibold text-white shadow-[0_18px_44px_rgba(31,92,82,0.22)] transition hover:bg-[var(--accent-main-strong)] disabled:opacity-60"
                  disabled={isBusy}
                  type="submit"
                >
                  {isBusy ? "注册中..." : "创建账户"}
                </button>

                <button
                  type="button"
                  className="mt-3 block w-full rounded-[1.35rem] border border-[var(--line-soft)] bg-white px-4 py-3.5 text-center text-sm font-semibold text-[var(--ink-strong)] transition hover:border-[var(--accent-main)] hover:text-[var(--accent-main)]"
                  onClick={() => {
                    setMode("login");
                    setSuccessMsg(null);
                  }}
                >
                  已有账户？返回登录
                </button>
              </form>
            )}

            <div className="mt-8 grid gap-3 md:grid-cols-2">
              <div className="rounded-[1.5rem] border border-[var(--line-soft)] bg-white/82 p-4">
                <p className="text-sm font-semibold text-[var(--ink-strong)]">
                  患者入口
                </p>
                <p className="mt-2 text-sm leading-6 text-[var(--ink-body)]">
                  预约服务、导诊助手、我的服务、个人档案
                </p>
              </div>
              <div className="rounded-[1.5rem] border border-[var(--line-soft)] bg-white/82 p-4">
                <p className="text-sm font-semibold text-[var(--ink-strong)]">
                  管理员入口
                </p>
                <p className="mt-2 text-sm leading-6 text-[var(--ink-body)]">
                  知识库、会话排查、Trace、审计日志
                </p>
              </div>
            </div>
          </section>
        </div>
      </div>
    </main>
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
      <span className="text-sm font-semibold text-[var(--ink-body)]">
        {label}
      </span>
      <input
        className="mt-2 w-full rounded-[1.1rem] border border-[var(--line-soft)] bg-white/80 px-4 py-3 text-sm text-[var(--ink-strong)] outline-none transition focus:border-[var(--accent-main)]"
        type={type}
        value={value}
        onChange={(event) => onChange(event.target.value)}
      />
    </label>
  );
}
