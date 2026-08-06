"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import type { ReactNode } from "react";

const navItems = [
  { href: "/admin", label: "总览" },
  { href: "/admin/knowledge", label: "知识库" },
  { href: "/admin/conversations", label: "会话" },
  { href: "/admin/traces", label: "Trace" },
  { href: "/admin/audit-logs", label: "审计" },
];

type AdminShellProps = {
  title: string;
  description: string;
  children: ReactNode;
  actions?: ReactNode;
};

export function AdminShell({ title, description, children, actions }: AdminShellProps) {
  const pathname = usePathname();

  return (
    <main className="min-h-screen bg-[linear-gradient(180deg,#edf6f0_0%,#f8faf7_42%,#ffffff_100%)] px-4 py-6 text-slate-950 sm:px-6">
      <section className="mx-auto max-w-7xl">
        <header className="rounded-[1.75rem] border border-emerald-100 bg-white/90 p-5 shadow-[0_25px_80px_rgba(21,60,52,0.08)] backdrop-blur">
          <div className="flex flex-wrap items-start justify-between gap-4">
            <div>
              <div className="flex items-center gap-3">
                <Link href="/" className="text-sm font-semibold text-emerald-700">
                  Medora
                </Link>
              </div>
              <h1 className="mt-3 text-2xl font-semibold sm:text-3xl">{title}</h1>
              <p className="mt-2 max-w-3xl text-sm leading-7 text-slate-600 sm:text-base">
                {description}
              </p>
            </div>
            <div className="flex flex-wrap items-center gap-3">{actions}</div>
          </div>

          <nav className="mt-5 flex flex-wrap gap-2">
            {navItems.map((item) => {
              const isActive =
                pathname === item.href || (item.href !== "/admin" && pathname.startsWith(item.href));
              return (
                <Link
                  key={item.href}
                  href={item.href}
                  className={`rounded-full px-4 py-2 text-sm font-semibold transition ${
                    isActive
                      ? "bg-slate-950 text-white"
                      : "border border-slate-200 bg-white text-slate-700 hover:border-slate-400"
                  }`}
                >
                  {item.label}
                </Link>
              );
            })}
          </nav>
        </header>

        <div className="mt-5">{children}</div>
      </section>
    </main>
  );
}
