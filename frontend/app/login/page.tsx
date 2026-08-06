"use client";

import { useState } from "react";
import type { FormEvent } from "react";
import { useRouter } from "next/navigation";
import { apiFetch } from "@/lib/api/client";
import { setTokens } from "@/lib/auth/token";
import { useAsyncAction } from "@/lib/hooks/use-async-action";
import type { LoginResult, RegisterResult } from "@/lib/types/api";

export default function LoginPage() {
  const router = useRouter();
  const { isBusy, error, runAction } = useAsyncAction();
  const [mode, setMode] = useState<"login" | "register">("login");
  const [successMsg, setSuccessMsg] = useState<string | null>(null);

  const [loginForm, setLoginForm] = useState({
    account: "",
    password: "",
    loginType: "PASSWORD",
  });

  const [registerForm, setRegisterForm] = useState({
    mobile: "",
    email: "",
    password: "",
    displayName: "",
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
      setSuccessMsg(`注册成功：${result.displayName}，请使用新账号登录。`);
      setLoginForm((prev) => ({
        ...prev,
        account: registerForm.email || registerForm.mobile,
        password: registerForm.password,
      }));
      setMode("login");
    });
  }

  return (
    <main className="relative flex min-h-screen items-center justify-center overflow-hidden px-4 py-8">
      {/* ── Decorative background elements ── */}
      <div className="pointer-events-none absolute inset-0">
        <div className="absolute -left-32 -top-32 h-96 w-96 rounded-full bg-[rgba(31,92,82,0.08)] blur-3xl" />
        <div className="absolute -bottom-32 -right-32 h-96 w-96 rounded-full bg-[rgba(184,132,81,0.08)] blur-3xl" />
        <div className="absolute left-1/2 top-1/2 h-[500px] w-[500px] -translate-x-1/2 -translate-y-1/2 rounded-full bg-[rgba(31,92,82,0.03)] blur-3xl" />
      </div>

      <div className="relative flex w-full max-w-[1024px] overflow-hidden rounded-[2.4rem] bg-white shadow-[0_40px_120px_rgba(20,42,39,0.12)]">
        {/* ── Left: Brand Panel ── */}
        <section className="relative hidden w-[45%] bg-[linear-gradient(145deg,#143b35_0%,#1f5c52_60%,#2c776c_100%)] p-10 text-white md:block">
          <div className="flex h-full flex-col justify-between">
            <div>
              <div className="flex items-center gap-3">
                <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-white/14 text-lg font-semibold tracking-tight backdrop-blur">
                  +
                </div>
                <div>
                  <div className="text-sm font-semibold tracking-[0.2em] text-white/70">
                    MEDORA
                  </div>
                </div>
              </div>
            </div>

            <div className="space-y-4">
              <h1 className="medora-display text-[2.6rem] leading-[1.08] tracking-[-0.03em] text-white">
                医疗智能助手
              </h1>
              <p className="max-w-xs text-base leading-8 text-white/72">
                融合导诊、预约与连续服务，让每一步就诊决策都有 AI 辅助。
              </p>

              <div className="mt-8 space-y-4">
                <FeaturePoint icon="→" text="智能导诊，精准匹配科室与医生" />
                <FeaturePoint icon="→" text="一键预约，实时查看可用号源" />
                <FeaturePoint icon="→" text="会话延续，历史记录自动归档" />
              </div>
            </div>

            <p className="text-xs text-white/40">
              © 2026 Medora · 智能医疗服务平台
            </p>
          </div>
        </section>

        {/* ── Right: Form Panel ── */}
        <section className="flex w-full items-center px-8 py-12 sm:px-14 md:w-[55%]">
          <div className="w-full max-w-sm mx-auto">
            {/* Mobile brand mark */}
            <div className="mb-8 flex items-center gap-3 md:hidden">
              <div className="flex h-9 w-9 items-center justify-center rounded-xl bg-[var(--accent-main)] text-sm font-semibold text-white">
                +
              </div>
              <span className="text-sm font-semibold tracking-[0.2em] text-[var(--accent-main)]">
                MEDORA
              </span>
            </div>

            <div className="mb-8">
              <h2 className="text-2xl font-semibold text-[var(--ink-strong)]">
                {mode === "login" ? "欢迎登录" : "创建账户"}
              </h2>
              <p className="mt-2 text-sm text-[var(--ink-body)]">
                {mode === "login"
                  ? "登录以使用 Medora 医疗智能服务"
                  : "注册一个账户开始使用"}
              </p>
            </div>

            {error ? (
              <div className="mb-6 rounded-[1.2rem] border border-[rgba(190,92,67,0.18)] bg-[var(--danger-soft)] px-4 py-3 text-sm leading-6 text-[#8a4a3a]">
                {error}
              </div>
            ) : null}

            {successMsg ? (
              <div className="mb-6 rounded-[1.2rem] border border-[rgba(31,92,82,0.18)] bg-[var(--bg-soft)] px-4 py-3 text-sm leading-6 text-[var(--accent-main-strong)]">
                {successMsg}
              </div>
            ) : null}

            {mode === "login" ? (
              <form onSubmit={handleLogin} className="space-y-5">
                <Field
                  label="手机号或邮箱"
                  placeholder="请输入手机号或邮箱"
                  value={loginForm.account}
                  onChange={(v) =>
                    setLoginForm((prev) => ({ ...prev, account: v }))
                  }
                />
                <Field
                  label="密码"
                  type="password"
                  placeholder="请输入密码"
                  value={loginForm.password}
                  onChange={(v) =>
                    setLoginForm((prev) => ({ ...prev, password: v }))
                  }
                />

                <button
                  className="block w-full rounded-[1.35rem] bg-[var(--accent-main)] px-4 py-3.5 text-center text-sm font-semibold text-white shadow-[0_18px_44px_rgba(31,92,82,0.22)] transition hover:bg-[var(--accent-main-strong)] disabled:opacity-60"
                  disabled={isBusy}
                  type="submit"
                >
                  {isBusy ? "登录中..." : "登录"}
                </button>

                <button
                  type="button"
                  className="block w-full rounded-[1.35rem] border border-[var(--line-soft)] bg-white px-4 py-3.5 text-center text-sm font-semibold text-[var(--ink-strong)] transition hover:border-[var(--accent-main)] hover:text-[var(--accent-main)]"
                  onClick={() => {
                    setMode("register");
                    setSuccessMsg(null);
                  }}
                >
                  没有账户？注册一个
                </button>
              </form>
            ) : (
              <form onSubmit={handleRegister} className="space-y-5">
                <Field
                  label="显示名称"
                  placeholder="请输入姓名"
                  value={registerForm.displayName}
                  onChange={(v) =>
                    setRegisterForm((prev) => ({ ...prev, displayName: v }))
                  }
                />
                <Field
                  label="手机号"
                  placeholder="请输入手机号"
                  value={registerForm.mobile}
                  onChange={(v) =>
                    setRegisterForm((prev) => ({ ...prev, mobile: v }))
                  }
                />
                <Field
                  label="邮箱"
                  placeholder="请输入邮箱"
                  value={registerForm.email}
                  onChange={(v) =>
                    setRegisterForm((prev) => ({ ...prev, email: v }))
                  }
                />
                <Field
                  label="密码"
                  type="password"
                  placeholder="请设置密码"
                  value={registerForm.password}
                  onChange={(v) =>
                    setRegisterForm((prev) => ({ ...prev, password: v }))
                  }
                />

                <button
                  className="block w-full rounded-[1.35rem] bg-[var(--accent-main)] px-4 py-3.5 text-center text-sm font-semibold text-white shadow-[0_18px_44px_rgba(31,92,82,0.22)] transition hover:bg-[var(--accent-main-strong)] disabled:opacity-60"
                  disabled={isBusy}
                  type="submit"
                >
                  {isBusy ? "注册中..." : "创建账户"}
                </button>

                <button
                  type="button"
                  className="block w-full rounded-[1.35rem] border border-[var(--line-soft)] bg-white px-4 py-3.5 text-center text-sm font-semibold text-[var(--ink-strong)] transition hover:border-[var(--accent-main)] hover:text-[var(--accent-main)]"
                  onClick={() => {
                    setMode("login");
                    setSuccessMsg(null);
                  }}
                >
                  已有账户？返回登录
                </button>
              </form>
            )}
          </div>
        </section>
      </div>
    </main>
  );
}

function FeaturePoint({ icon, text }: { icon: string; text: string }) {
  return (
    <div className="flex items-center gap-3">
      <span className="flex h-6 w-6 items-center justify-center rounded-full bg-white/12 text-xs text-white/80">
        {icon}
      </span>
      <span className="text-sm text-white/76">{text}</span>
    </div>
  );
}

function Field({
  label,
  value,
  onChange,
  type = "text",
  placeholder,
}: {
  label: string;
  value: string;
  onChange: (value: string) => void;
  type?: string;
  placeholder?: string;
}) {
  return (
    <label className="block">
      <span className="text-sm font-semibold text-[var(--ink-body)]">
        {label}
      </span>
      <input
        className="mt-2 w-full rounded-[1.1rem] border border-[var(--line-soft)] bg-[rgba(255,255,255,0.8)] px-4 py-3 text-sm text-[var(--ink-strong)] outline-none transition placeholder:text-[var(--ink-subtle)] focus:border-[var(--accent-main)]"
        type={type}
        value={value}
        placeholder={placeholder}
        onChange={(event) => onChange(event.target.value)}
      />
    </label>
  );
}
