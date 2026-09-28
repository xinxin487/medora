"use client";

import { useState } from "react";
import { apiFetch } from "@/lib/api/client";
import { useAuth } from "@/lib/hooks/use-auth";
import { WorkspaceShell } from "@/components/workspace-shell";
import { Panel } from "@/components/panel";

type Diagnostic = { traceId: string; profile: string; budget: Record<string, number>; sourceCounts: Record<string, number>; reasonCounts: Record<string, number>; itemFingerprints: string[]; decisions: Array<{ source: string; fingerprint: string; selected: boolean; reasonCode: string; estimatedTokens: number }>; degraded: boolean };

export default function AdminContextPage() {
  const { isLoading } = useAuth("ADMIN"); const [traceId, setTraceId] = useState(""); const [data, setData] = useState<Diagnostic | null>(null); const [error, setError] = useState("");
  async function load() { try { setError(""); setData(await apiFetch<Diagnostic>(`/admin/context-diagnostics/${encodeURIComponent(traceId)}`)); } catch (e) { setError(e instanceof Error ? e.message : "加载失败"); } }
  if (isLoading) return null;
  return <WorkspaceShell role="admin" activeKey="traces" title="上下文诊断" subtitle="仅展示脱敏来源、预算和筛选原因。"><Panel title="查询 Trace" eyebrow="Context"><div className="flex gap-2"><input className="flex-1 rounded border p-2" value={traceId} onChange={e => setTraceId(e.target.value)} placeholder="Trace ID" /><button className="rounded bg-[var(--accent-main)] px-4 text-white" onClick={load}>查询</button></div>{error && <p className="mt-3 text-sm text-red-600">{error}</p>}{data && <div className="mt-5 space-y-4 text-sm"><p>Profile: {data.profile} {data.degraded && <span className="text-amber-600">DEGRADED</span>}</p><pre>{JSON.stringify({ budget: data.budget, sources: data.sourceCounts, reasons: data.reasonCounts, decisions: data.decisions ?? [] }, null, 2)}</pre></div>}</Panel></WorkspaceShell>;
}
