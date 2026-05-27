"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { apiFetch } from "@/lib/api/client";
import { useAuth } from "@/lib/hooks/use-auth";
import type { PageResponse } from "@/lib/types";
import { WorkspaceShell } from "@/components/workspace-shell";
import { Panel } from "@/components/panel";
import { MetricCard } from "@/components/metric-card";
import { Badge } from "@/components/badge";

const quickLinks = [
  {
    href: "/admin/knowledge",
    title: "知识库管理",
    description: "上传文档、管理索引状态与发布。",
  },
  {
    href: "/admin/conversations",
    title: "会话查看",
    description: "按用户和会话维度查看摘要与消息记录。",
  },
  {
    href: "/admin/traces",
    title: "链路追踪",
    description: "查看路由决策、工具调用与检索耗时。",
  },
  {
    href: "/admin/audit-logs",
    title: "审计日志",
    description: "追踪系统操作与关键业务事件。",
  },
];

export default function AdminOverviewPage() {
  const { isLoading: authLoading } = useAuth("ADMIN");

  const [stats, setStats] = useState({
    knowledgeDocuments: 0,
    conversations: 0,
    traces: 0,
    auditLogs: 0,
  });
  const [status, setStatus] = useState("正在加载概览数据。");
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    void loadOverview();
  }, []);

  async function loadOverview() {
    setError(null);
    setStatus("正在读取统计数据。");
    try {
      const [documents, conversations, traces, auditLogs] = await Promise.all([
        apiFetch<PageResponse<unknown>>("/admin/knowledge/documents?pageSize=1"),
        apiFetch<PageResponse<unknown>>("/admin/conversations?pageSize=1"),
        apiFetch<PageResponse<unknown>>("/admin/agent-traces?pageSize=1"),
        apiFetch<PageResponse<unknown>>("/admin/audit-logs?pageSize=1"),
      ]);
      setStats({
        knowledgeDocuments: documents.total,
        conversations: conversations.total,
        traces: traces.total,
        auditLogs: auditLogs.total,
      });
      setStatus("概览数据已更新。");
    } catch (cause) {
      if (cause instanceof Error) {
        setError(cause.message);
      } else {
        setError("加载失败，请稍后重试。");
      }
    }
  }

  if (authLoading) return <LoadingSkeleton />;

  return (
    <WorkspaceShell
      role="admin"
      activeKey="dashboard"
      title="管理控制台"
      subtitle="知识库、会话、Trace 与审计集中管理。"
      actions={null}
      aside={
        <>
          <Panel title="系统状态" eyebrow="System Status">
            <p className="text-sm leading-7 text-[var(--ink-body)]">{status}</p>
            {error ? (
              <div className="mt-4 rounded-[1.2rem] border border-[rgba(190,92,67,0.18)] bg-[var(--danger-soft)] px-4 py-3 text-sm leading-6 text-[#8a4a3a]">
                {error}
              </div>
            ) : null}
            <div className="mt-4 space-y-2">
              <div className="flex items-center justify-between gap-2 rounded-2xl border border-[var(--line-soft)] bg-white/82 px-4 py-3">
                <span className="text-sm text-[var(--ink-body)]">API 状态</span>
                <Badge tone="success">正常</Badge>
              </div>
              <div className="flex items-center justify-between gap-2 rounded-2xl border border-[var(--line-soft)] bg-white/82 px-4 py-3">
                <span className="text-sm text-[var(--ink-body)]">知识库任务</span>
                <Badge tone="soft">进行中</Badge>
              </div>
            </div>
          </Panel>

          <Panel title="最近事件" eyebrow="Recent Events">
            <div className="space-y-2 text-sm text-[var(--ink-body)]">
              <div className="rounded-2xl border border-[var(--line-soft)] bg-white/82 px-4 py-3">
                知识库已就绪，可随时上传新文档。
              </div>
              <div className="rounded-2xl border border-[var(--line-soft)] bg-white/82 px-4 py-3">
                会话与 Trace 数据实时同步中。
              </div>
            </div>
          </Panel>
        </>
      }
    >
      <div className="grid gap-5 md:grid-cols-2 xl:grid-cols-4">
        <MetricCard
          label="知识文档"
          value={`${stats.knowledgeDocuments}`}
          hint="已上传的知识库文档总量。"
        />
        <MetricCard
          label="会话总数"
          value={`${stats.conversations}`}
          hint="所有用户的导诊会话数量。"
        />
        <MetricCard
          label="Trace 总数"
          value={`${stats.traces}`}
          hint="Agent 调用链路追踪记录。"
        />
        <MetricCard
          label="审计事件"
          value={`${stats.auditLogs}`}
          hint="系统级操作审计日志条数。"
        />
      </div>

      <Panel title="快捷入口" eyebrow="Quick Access">
        <div className="grid gap-4 sm:grid-cols-2">
          {quickLinks.map((item) => (
            <Link
              key={item.href}
              href={item.href}
              className="rounded-[1.5rem] border border-[var(--line-soft)] bg-white/84 p-4 transition hover:border-[var(--accent-main)] hover:bg-white"
            >
              <div className="flex items-center justify-between gap-3">
                <h3 className="text-base font-semibold text-[var(--ink-strong)]">
                  {item.title}
                </h3>
                <span className="text-sm font-semibold text-[var(--accent-main)]">
                  进入
                </span>
              </div>
              <p className="mt-2 text-sm leading-6 text-[var(--ink-body)]">
                {item.description}
              </p>
            </Link>
          ))}
        </div>
      </Panel>
    </WorkspaceShell>
  );
}

function LoadingSkeleton() {
  return (
    <main className="min-h-screen px-4 py-5 sm:px-6">
      <div className="mx-auto max-w-[1520px]">
        <div className="grid gap-5 xl:grid-cols-[280px_minmax(0,1fr)_320px]">
          <div className="h-[calc(100vh-2.5rem)] animate-pulse rounded-[2rem] bg-slate-200" />
          <div className="space-y-5">
            <div className="h-48 animate-pulse rounded-[1.75rem] bg-slate-100" />
            <div className="grid gap-5 md:grid-cols-2">
              <div className="h-32 animate-pulse rounded-[1.75rem] bg-slate-100" />
              <div className="h-32 animate-pulse rounded-[1.75rem] bg-slate-100" />
            </div>
          </div>
          <div className="space-y-5">
            <div className="h-48 animate-pulse rounded-[1.75rem] bg-slate-100" />
            <div className="h-36 animate-pulse rounded-[1.75rem] bg-slate-100" />
          </div>
        </div>
      </div>
    </main>
  );
}
