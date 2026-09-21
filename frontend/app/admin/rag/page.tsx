"use client";

import { FormEvent, useState } from "react";
import { WorkspaceShell } from "@/components/workspace-shell";
import { Panel } from "@/components/panel";
import { apiFetch } from "@/lib/api/client";
import { useAuth } from "@/lib/hooks/use-auth";
import { useAsyncAction } from "@/lib/hooks/use-async-action";

type Item = { documentId: string; childChunkId: number; parentChunkId: number | null; sectionPath: string; pageNumber: number; lexicalRank: number; vectorRank: number; rrfScore: number; rerankScore: number; snippet: string; fallbackReason: string };
type Diagnostic = { query: string; appliedFilters: Record<string, string>; items: Item[]; latencyMs: number; fallbackReason: string };

export default function RagDiagnosticsPage() {
  const { isLoading } = useAuth("ADMIN");
  const { isBusy, error, runAction } = useAsyncAction();
  const [query, setQuery] = useState("");
  const [bizScope, setBizScope] = useState("FAQ");
  const [result, setResult] = useState<Diagnostic | null>(null);
  async function submit(event: FormEvent) {
    event.preventDefault();
    await runAction(async () => setResult(await apiFetch<Diagnostic>("/admin/rag-diagnostics/query", { method: "POST", body: JSON.stringify({ query, bizScope, topK: 5 }) })));
  }
  if (isLoading) return null;
  return <WorkspaceShell role="admin" activeKey="knowledge" title="RAG 诊断" subtitle="查看过滤、融合与重排后的脱敏检索证据。">
    <Panel title="测试查询" eyebrow="Diagnostics">
      <form onSubmit={submit} className="flex gap-3"><input className="flex-1 rounded-xl border p-3" value={query} onChange={(e) => setQuery(e.target.value)} placeholder="例如：如何取消挂号" /><select className="rounded-xl border p-3" value={bizScope} onChange={(e) => setBizScope(e.target.value)}><option value="FAQ">FAQ</option><option value="APPOINTMENT_RULE">挂号规则</option><option value="GUIDE">导诊说明</option></select><button className="rounded-xl bg-[var(--accent-main)] px-5 text-white disabled:opacity-60" disabled={isBusy || !query.trim()}>运行</button></form>
      {error ? <p className="mt-3 text-sm text-red-700">{error}</p> : null}
    </Panel>
    {result ? <Panel title={`结果 · ${result.latencyMs}ms`} eyebrow={`Filters: ${Object.values(result.appliedFilters).join(" / ")}`}><div className="overflow-x-auto"><table className="w-full text-left text-sm"><thead><tr className="border-b"><th className="p-2">文档 / 子块</th><th className="p-2">路径</th><th className="p-2">RRF / 重排</th><th className="p-2">证据片段</th></tr></thead><tbody>{result.items.map((item) => <tr key={item.childChunkId} className="border-b align-top"><td className="p-2">{item.documentId}<br />#{item.childChunkId}</td><td className="p-2">{item.sectionPath}</td><td className="p-2">{item.rrfScore.toFixed(3)} / {item.rerankScore.toFixed(3)}<br /><span className="text-xs text-slate-500">{item.fallbackReason}</span></td><td className="p-2">{item.snippet}</td></tr>)}</tbody></table></div>{result.items.length === 0 ? <p className="p-3 text-sm text-slate-500">无匹配结果：{result.fallbackReason}</p> : null}</Panel> : null}
  </WorkspaceShell>;
}
