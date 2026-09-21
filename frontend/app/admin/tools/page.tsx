"use client";

import { useEffect, useState } from "react";
import { WorkspaceShell } from "@/components/workspace-shell";
import { Panel } from "@/components/panel";
import { Badge } from "@/components/badge";
import { apiFetch } from "@/lib/api/client";
import { useAuth } from "@/lib/hooks/use-auth";
import type { PageResponse } from "@/lib/types";

type ToolAuditItem = {
  auditId: string; toolName: string; toolVersion: string; sourceType: string; riskLevel: string;
  agentName: string | null; conversationId: string | null; traceId: string | null; requestId: string | null;
  status: string; errorCode: string | null; durationMs: number; argumentsHash: string; argumentsSummary: string; createdAt: string;
};

export default function AdminToolsPage() {
  const { isLoading: authLoading } = useAuth("ADMIN");
  const [toolName, setToolName] = useState("");
  const [status, setStatus] = useState("");
  const [page, setPage] = useState<PageResponse<ToolAuditItem> | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function load() {
    setLoading(true); setError(null);
    try { setPage(await apiFetch<PageResponse<ToolAuditItem>>(`/admin/tool-audits?toolName=${encodeURIComponent(toolName)}&status=${encodeURIComponent(status)}&pageSize=30`)); }
    catch (cause) { setError(cause instanceof Error ? cause.message : "加载工具审计失败。"); }
    finally { setLoading(false); }
  }
  useEffect(() => { void load(); }, []);
  if (authLoading) return null;
  return <WorkspaceShell role="admin" activeKey="tools" title="工具网关" subtitle="查看 Agent 工具调用的结果、时延和脱敏审计摘要。" aside={<Panel title="筛选" eyebrow="Gateway audit"><form className="space-y-3" onSubmit={(event) => { event.preventDefault(); void load(); }}><input className="w-full rounded-xl border p-3 text-sm" placeholder="工具名称" value={toolName} onChange={(event) => setToolName(event.target.value)} /><select className="w-full rounded-xl border p-3 text-sm" value={status} onChange={(event) => setStatus(event.target.value)}><option value="">全部状态</option><option value="SUCCESS">成功</option><option value="DENIED">拒绝</option><option value="TIMEOUT">超时</option><option value="FAILED">失败</option><option value="RATE_LIMITED">限流</option></select><button className="w-full rounded-full bg-[var(--accent-main)] px-4 py-2.5 text-sm font-semibold text-white disabled:opacity-60" disabled={loading}>{loading ? "加载中..." : "刷新"}</button></form><p className="mt-4 text-xs leading-5 text-[var(--ink-subtle)]">参数只展示脱敏摘要；原始参数和工具返回内容不会出现在后台。</p></Panel>}>
    {error ? <p className="rounded-xl bg-red-50 p-3 text-sm text-red-700">{error}</p> : null}
    <Panel title={`调用记录 · ${page?.total ?? 0} 条`} eyebrow="Sanitized only">
      {!page?.list.length ? <p className="p-4 text-sm text-[var(--ink-subtle)]">暂无工具调用记录。</p> : <div className="space-y-3">{page.list.map((item) => <article key={item.auditId} className="rounded-2xl border border-[var(--line-soft)] bg-white/80 p-4"><div className="flex flex-wrap items-center justify-between gap-3"><div className="flex items-center gap-2"><strong className="text-sm">{item.toolName}@{item.toolVersion}</strong><Badge>{item.status}</Badge><Badge tone="soft">{item.riskLevel}</Badge></div><span className="text-xs text-[var(--ink-subtle)]">{item.durationMs}ms · {new Date(item.createdAt).toLocaleString("zh-CN", { hour12: false })}</span></div><p className="mt-2 text-xs text-[var(--ink-subtle)]">Agent: {item.agentName ?? "-"} · Trace: {item.traceId ?? "-"} · 请求: {item.requestId ?? "-"}</p><details className="mt-3"><summary className="cursor-pointer text-xs font-semibold text-[var(--accent-main)]">查看脱敏参数摘要</summary><pre className="mt-2 overflow-x-auto rounded-xl bg-slate-950 p-3 text-xs text-slate-100">{item.argumentsSummary}</pre><p className="mt-2 text-xs text-[var(--ink-subtle)]">参数哈希：{item.argumentsHash}</p></details></article>)}</div>}
    </Panel>
  </WorkspaceShell>;
}
