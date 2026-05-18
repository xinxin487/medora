"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import type { ReactNode } from "react";

const navItems = [
  { href: "/", label: "首页" },
  { href: "/appointments", label: "预约中心" },
  { href: "/chat", label: "导诊助手" },
  { href: "/login", label: "登录/账户" },
];

type PatientShellProps = {
  children: ReactNode;
  title?: string;
  eyebrow?: string;
  description?: string;
  actions?: ReactNode;
};

export function PatientShell({
  children,
  title,
  eyebrow,
  description,
  actions,
}: PatientShellProps) {
  const pathname = usePathname();

  return (
    <main className="min-h-screen px-4 py-5 text-[var(--ink-strong)] sm:px-6 lg:px-8">
      <div className="mx-auto max-w-7xl">
        <header className="medora-surface sticky top-4 z-30 border border-[var(--line-soft)] px-4 py-4 backdrop-blur sm:px-6">
          <div className="flex flex-wrap items-center justify-between gap-4">
            <div className="flex items-center gap-3">
              <div className="flex h-11 w-11 items-center justify-center rounded-2xl border border-[rgba(31,92,82,0.18)] bg-[rgba(255,255,255,0.72)] text-sm font-semibold text-[var(--accent-main)] shadow-[0_16px_40px_rgba(24,56,49,0.08)]">
                M
              </div>
              <div>
                <Link href="/" className="medora-display text-xl font-semibold tracking-[-0.03em]">
                  Medora
                </Link>
                <p className="text-xs uppercase tracking-[0.22em] text-[var(--ink-subtle)]">
                  Human-Centered Medical Access
                </p>
              </div>
            </div>

            <nav className="flex flex-wrap items-center gap-2">
              {navItems.map((item) => {
                const active =
                  item.href === "/"
                    ? pathname === "/"
                    : pathname === item.href || pathname.startsWith(`${item.href}/`);
                return (
                  <Link
                    key={item.href}
                    href={item.href}
                    className={`rounded-full px-4 py-2 text-sm font-semibold transition ${
                      active
                        ? "bg-[var(--ink-strong)] text-white shadow-[0_14px_30px_rgba(20,42,39,0.16)]"
                        : "border border-[var(--line-soft)] bg-[rgba(255,255,255,0.72)] text-[var(--ink-body)] hover:border-[var(--accent-main)] hover:text-[var(--accent-main)]"
                    }`}
                  >
                    {item.label}
                  </Link>
                );
              })}
              <Link
                href="/admin"
                className="rounded-full border border-[rgba(184,132,81,0.24)] bg-[rgba(184,132,81,0.08)] px-4 py-2 text-sm font-semibold text-[var(--accent-warm)] transition hover:border-[var(--accent-warm)]"
              >
                后台
              </Link>
            </nav>
          </div>
        </header>

        {title ? (
          <section className="mt-5 grid gap-4 lg:grid-cols-[1.2fr_0.8fr]">
            <div className="medora-surface medora-grid border border-[var(--line-soft)] px-5 py-6 sm:px-7 sm:py-7">
              {eyebrow ? (
                <p className="text-xs font-semibold uppercase tracking-[0.26em] text-[var(--accent-main)]">
                  {eyebrow}
                </p>
              ) : null}
              <h1 className="medora-display mt-4 text-4xl font-semibold leading-[1.05] tracking-[-0.04em] sm:text-5xl">
                {title}
              </h1>
              {description ? (
                <p className="mt-4 max-w-3xl text-base leading-8 text-[var(--ink-body)] sm:text-lg">
                  {description}
                </p>
              ) : null}
            </div>

            <div className="medora-surface border border-[var(--line-soft)] bg-[linear-gradient(140deg,rgba(237,243,238,0.95),rgba(255,255,255,0.92))] px-5 py-6 sm:px-6">
              <p className="text-xs font-semibold uppercase tracking-[0.24em] text-[var(--ink-subtle)]">
                Quick Actions
              </p>
              <div className="mt-4 flex min-h-full flex-wrap items-center gap-3">{actions}</div>
            </div>
          </section>
        ) : null}

        <div className={title ? "mt-5" : "mt-6"}>{children}</div>

        <footer className="mt-10 border-t border-[rgba(20,42,39,0.08)] px-1 py-6 text-sm text-[var(--ink-subtle)]">
          <div className="flex flex-wrap items-center justify-between gap-3">
            <p>Medora 将预约服务、导诊助手和会话连续性整合在同一条用户路径中。</p>
            <p>Built for real API workflows, not demo placeholders.</p>
          </div>
        </footer>
      </div>
    </main>
  );
}
