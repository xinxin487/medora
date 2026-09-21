"use client";

import type { ReactNode } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { clearTokens } from "@/lib/auth/token";
import { joinClasses } from "@/lib/utils";

type WorkspaceRole = "patient" | "admin";

type WorkspaceNavItem = {
  key: string;
  href: string;
  label: string;
  caption: string;
};

const workspaceNav: Record<WorkspaceRole, WorkspaceNavItem[]> = {
  patient: [
    {
      key: "dashboard",
      href: "/app/dashboard",
      label: "工作台首页",
      caption: "总览",
    },
    {
      key: "appointments",
      href: "/app/appointments",
      label: "预约服务",
      caption: "号源与预约",
    },
    {
      key: "assistant",
      href: "/app/assistant",
      label: "导诊助手",
      caption: "会话与建议",
    },
    {
      key: "services",
      href: "/app/services",
      label: "我的服务",
      caption: "记录与进度",
    },
    {
      key: "profile",
      href: "/app/profile",
      label: "个人档案",
      caption: "资料与偏好",
    },
  ],
  admin: [
    {
      key: "dashboard",
      href: "/admin",
      label: "后台总览",
      caption: "概况",
    },
    {
      key: "knowledge",
      href: "/admin/knowledge",
      label: "知识库",
      caption: "文档与检索",
    },
    {
      key: "conversations",
      href: "/admin/conversations",
      label: "会话排查",
      caption: "消息与摘要",
    },
    {
      key: "traces",
      href: "/admin/traces",
      label: "Trace",
      caption: "链路与工具",
    },
    {
      key: "tools",
      href: "/admin/tools",
      label: "工具网关",
      caption: "调用与健康",
    },
    {
      key: "audit",
      href: "/admin/audit-logs",
      label: "审计日志",
      caption: "事件与归因",
    },
  ],
};

const workspaceMeta = {
  patient: {
    roleLabel: "患者服务台",
    badge: "在线",
    lead: "把导诊、预约和服务记录放进同一个工作区。",
    footerTitle: "今日服务",
  },
  admin: {
    roleLabel: "管理控制台",
    badge: "稳定",
    lead: "把知识、会话、追踪和审计集中到一套后台。",
    footerTitle: "待处理事项",
  },
} as const;

type WorkspaceShellProps = {
  role: WorkspaceRole;
  activeKey: string;
  title: string;
  subtitle?: string;
  actions?: ReactNode;
  children: ReactNode;
  aside?: ReactNode;
  userDisplayName?: string;
};

export function WorkspaceShell({
  role,
  activeKey,
  title,
  subtitle,
  actions,
  children,
  aside,
  userDisplayName,
}: WorkspaceShellProps) {
  const router = useRouter();
  const navItems = workspaceNav[role];
  const meta = workspaceMeta[role];

  function handleLogout() {
    clearTokens();
    router.replace("/login");
  }

  return (
    <main className="min-h-screen px-4 py-5 sm:px-6">
      <div className="mx-auto max-w-[1520px]">
        <div className="grid gap-5 xl:grid-cols-[280px_minmax(0,1fr)_320px]">
          {/* ── Left Sidebar ── */}
          <aside className="overflow-hidden rounded-[2rem] bg-[linear-gradient(180deg,#143b35_0%,#0f2d28_100%)] p-5 text-white shadow-[0_30px_80px_rgba(20,42,39,0.22)] xl:sticky xl:top-5 xl:h-[calc(100vh-2.5rem)] xl:overflow-y-auto">
            <div className="flex items-center justify-between gap-3">
              <div>
                <div className="text-xs font-semibold uppercase tracking-[0.28em] text-white/60">
                  Medora
                </div>
                <div className="mt-2 text-lg font-semibold">
                  {userDisplayName ?? meta.roleLabel}
                </div>
              </div>
              <span className="rounded-full border border-white/12 bg-white/10 px-3 py-1 text-xs font-semibold text-white/78">
                {meta.badge}
              </span>
            </div>

            <div className="mt-6 rounded-[1.7rem] border border-white/10 bg-white/8 p-4">
              <div className="flex items-center justify-between gap-3">
                <p className="text-sm font-semibold">{meta.roleLabel}</p>
                <span className="rounded-full bg-white/10 px-3 py-1 text-xs font-semibold text-white/72">
                  {role === "patient" ? "Patient" : "Admin"}
                </span>
              </div>
              <p className="mt-3 text-sm leading-7 text-white/72">
                {meta.lead}
              </p>
            </div>

            <nav className="mt-6 space-y-2">
              {navItems.map((item) => {
                const isActive = item.key === activeKey;
                return (
                  <Link
                    key={item.key}
                    href={item.href}
                    className={joinClasses(
                      "flex items-center justify-between gap-3 rounded-[1.35rem] border px-4 py-3 transition",
                      isActive
                        ? "border-white/15 bg-white text-[var(--accent-main-strong)] shadow-[0_14px_40px_rgba(0,0,0,0.18)]"
                        : "border-white/10 bg-white/8 text-white hover:bg-white/12",
                    )}
                  >
                    <div>
                      <div className="text-sm font-semibold">{item.label}</div>
                      <div
                        className={joinClasses(
                          "mt-1 text-xs",
                          isActive
                            ? "text-[var(--accent-main)]"
                            : "text-white/60",
                        )}
                      >
                        {item.caption}
                      </div>
                    </div>
                    <span
                      className={joinClasses(
                        "h-2.5 w-2.5 rounded-full",
                        isActive ? "bg-[var(--accent-main)]" : "bg-white/18",
                      )}
                    />
                  </Link>
                );
              })}
            </nav>

            <div className="mt-auto pt-6">
              <button
                onClick={handleLogout}
                className="w-full rounded-[1.35rem] border border-white/10 bg-white/6 px-4 py-3 text-sm text-white/72 transition hover:bg-white/12 hover:text-white"
              >
                退出登录
              </button>
            </div>
          </aside>

          {/* ── Main Content ── */}
          <section className="space-y-5">
            <header className="medora-surface medora-grid border border-white/60 p-6 sm:p-8">
              <div className="flex flex-wrap items-start justify-between gap-4">
                <div className="max-w-3xl">
                  <div className="text-xs font-semibold uppercase tracking-[0.24em] text-[var(--accent-main)]">
                    {role === "patient" ? "Care Hub" : "Operations Hub"}
                  </div>
                  <h1 className="medora-display mt-3 text-4xl leading-tight text-[var(--ink-strong)] sm:text-[2.9rem]">
                    {title}
                  </h1>
                  {subtitle ? (
                    <p className="mt-4 text-sm leading-7 text-[var(--ink-body)] sm:text-base">
                      {subtitle}
                    </p>
                  ) : null}
                </div>
                {actions ? (
                  <div className="flex flex-wrap items-center gap-3">
                    {actions}
                  </div>
                ) : null}
              </div>
            </header>
            {children}
          </section>

          {/* ── Right Aside ── */}
          {aside ? (
            <aside className="space-y-5">{aside}</aside>
          ) : (
            <div className="hidden xl:block" />
          )}
        </div>
      </div>
    </main>
  );
}
