"use client";

import { startTransition, useEffect, useState } from "react";
import { apiFetch } from "@/lib/api/client";
import { useAuth } from "@/lib/hooks/use-auth";
import type { PageResponse } from "@/lib/types";
import { WorkspaceShell } from "@/components/workspace-shell";
import { Panel } from "@/components/panel";
import { Badge } from "@/components/badge";

type TraceItem = {
  traceId: string;
  conversationId: string;
  messageId: string | null;
  intentType: string;
  routeMode: string;
  status: string;
  latencyMs: number;
  errorMessage: string | null;
  createdAt: string;
};

type TraceDetail = TraceItem & {
  retrievalJson: string;
  toolEventsJson: string;
};

function prettyJson(value: string) {
  try {
    return JSON.stringify(JSON.parse(value), null, 2);
  } catch {
    return value;
  }
}

function formatTime(value: string) {
  return new Date(value).toLocaleString("zh-CN", { hour12: false });
}

export default function AdminTracePage() {
  const { isLoading: authLoading } = useAuth("ADMIN");

  const [traceId, setTraceId] = useState("");
  const [conversationId, setConversationId] = useState("");
  const [status, setStatus] = useState("");
  const [pageNo, setPageNo] = useState(1);
  const [page, setPage] = useState<PageResponse<TraceItem> | null>(null);
  const [selectedTraceId, setSelectedTraceId] = useState<string | null>(null);
  const [detail, setDetail] = useState<TraceDetail | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    void loadList(pageNo);
  }, [pageNo]);

  useEffect(() => {
    const params = new URLSearchParams(window.location.search);
    const cid = params.get("conversationId");
    if (cid) setConversationId(cid);
  }, []);

  useEffect(() => {
    if (!selectedTraceId) return;
    void loadDetail(selectedTraceId);
  }, [selectedTraceId]);

  async function loadList(targetPage: number) {
    setLoading(true);
    setError(null);
    try {
      const result = await apiFetch<PageResponse<TraceItem>>(
        `/admin/agent-traces?pageNo=${targetPage}&pageSize=20&traceId=${encodeURIComponent(traceId)}&conversationId=${encodeURIComponent(conversationId)}&status=${encodeURIComponent(status)}`,
      );
      setPage(result);
      if (!selectedTraceId && result.list.length > 0) {
        startTransition(() => setSelectedTraceId(result.list[0].traceId));
      }
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "加载 Trace 列表失败。");
    } finally {
      setLoading(false);
    }
  }

  async function loadDetail(targetTraceId: string) {
    try {
      const result = await apiFetch<TraceDetail>(
        `/admin/agent-traces/${targetTraceId}`,
      );
      setDetail(result);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "加载详情失败。");
    }
  }

  if (authLoading) return <LoadingSkeleton />;

  return (
    <WorkspaceShell
      role="admin"
      activeKey="traces"
      title="链路追踪"
      subtitle="路由决策、工具事件、检索结果和耗时。"
      aside={
        <Panel title="筛选 Trace" eyebrow="Filters">
          <form
            onSubmit={(e) => {
              e.preventDefault();
              setPageNo(1);
              void loadList(1);
            }}
            className="space-y-3"
          >
            <input
              className="w-full rounded-[1.1rem] border border-[var(--line-soft)] bg-white/80 px-3 py-2.5 text-sm outline-none transition focus:border-[var(--accent-main)]"
              placeholder="Trace ID"
              value={traceId}
              onChange={(e) => setTraceId(e.target.value)}
            />
            <input
              className="w-full rounded-[1.1rem] border border-[var(--line-soft)] bg-white/80 px-3 py-2.5 text-sm outline-none transition focus:border-[var(--accent-main)]"
              placeholder="会话 ID"
              value={conversationId}
              onChange={(e) => setConversationId(e.target.value)}
            />
            <select
              className="w-full rounded-[1.1rem] border border-[var(--line-soft)] bg-white/80 px-3 py-2.5 text-sm outline-none transition focus:border-[var(--accent-main)]"
              value={status}
              onChange={(e) => setStatus(e.target.value)}
            >
              <option value="">全部状态</option>
              <option value="SUCCESS">SUCCESS</option>
              <option value="FAILED">FAILED</option>
            </select>
            <button
              className="w-full rounded-full border border-[var(--line-soft)] px-4 py-2.5 text-sm font-semibold text-[var(--ink-body)] disabled:opacity-60"
              disabled={loading}
              type="submit"
            >
              {loading ? "加载中..." : "刷新列表"}
            </button>
          </form>

          <div className="mt-5 space-y-2">
            {page?.list.map((item) => {
              const active = item.traceId === selectedTraceId;
              return (
                <button
                  key={item.traceId}
                  type="button"
                  onClick={() =>
                    startTransition(() => setSelectedTraceId(item.traceId))
                  }
                  className={`w-full rounded-[1.3rem] border px-3 py-3 text-left transition ${
                    active
                      ? "border-[var(--accent-main)] bg-[rgba(31,92,82,0.08)]"
                      : "border-[var(--line-soft)] bg-white/78 hover:border-[var(--accent-warm)]"
                  }`}
                >
                  <div className="flex items-center justify-between gap-2">
                    <p className="text-sm font-semibold text-[var(--ink-strong)]">
                      {item.intentType}
                    </p>
                    <Badge
                      tone={item.status === "SUCCESS" ? "success" : "warning"}
                    >
                      {item.status}
                    </Badge>
                  </div>
                  <p className="mt-1 text-xs text-[var(--ink-subtle)]">
                    {item.routeMode} · {item.latencyMs} ms
                  </p>
                </button>
              );
            })}
            {page?.list.length === 0 && (
              <p className="py-4 text-center text-sm text-[var(--ink-subtle)]">
                暂无 Trace
              </p>
            )}
          </div>

          {page && (
            <div className="mt-3 flex items-center justify-between gap-2">
              <button
                className="rounded-full border border-[var(--line-soft)] px-3 py-1 text-xs text-[var(--ink-body)] disabled:opacity-50"
                disabled={pageNo <= 1 || loading}
                onClick={() => setPageNo((c) => Math.max(c - 1, 1))}
                type="button"
              >
                上一页
              </button>
              <span className="text-xs text-[var(--ink-subtle)]">
                第 {pageNo} 页 · {page.total} 条
              </span>
              <button
                className="rounded-full border border-[var(--line-soft)] px-3 py-1 text-xs text-[var(--ink-body)] disabled:opacity-50"
                disabled={!page.hasNext || loading}
                onClick={() => setPageNo((c) => c + 1)}
                type="button"
              >
                下一页
              </button>
            </div>
          )}
        </Panel>
      }
    >
      {error ? (
        <div className="rounded-[1.2rem] border border-[rgba(190,92,67,0.18)] bg-[var(--danger-soft)] px-4 py-3 text-sm leading-6 text-[#8a4a3a]">
          {error}
        </div>
      ) : null}

      {detail ? (
        <>
          <Panel title={detail.intentType} eyebrow="Trace Detail">
            <div className="flex flex-wrap items-start justify-between gap-4">
              <div>
                <p className="text-sm text-[var(--ink-body)]">
                  {detail.routeMode} · {detail.status} · {detail.latencyMs} ms
                </p>
              </div>
              <div className="rounded-[1.2rem] border border-[var(--line-soft)] bg-white/72 px-4 py-3 text-sm text-[var(--ink-subtle)]">
                <div>创建：{formatTime(detail.createdAt)}</div>
                <div>会话：{detail.conversationId}</div>
                <div>消息：{detail.messageId ?? "无"}</div>
              </div>
            </div>
            {detail.errorMessage && (
              <div className="mt-4 rounded-[1.2rem] border border-[rgba(190,92,67,0.18)] bg-[var(--danger-soft)] px-4 py-3 text-sm text-[#8a4a3a]">
                {detail.errorMessage}
              </div>
            )}
          </Panel>

          <div className="grid gap-5 xl:grid-cols-2">
            <Panel title="检索结果" eyebrow="Retrieval">
              <pre className="overflow-x-auto rounded-2xl bg-[#14362f] p-4 text-xs leading-6 text-emerald-100">
                {prettyJson(detail.retrievalJson)}
              </pre>
            </Panel>
            <Panel title="工具事件" eyebrow="Tool Events">
              <pre className="overflow-x-auto rounded-2xl bg-[#14362f] p-4 text-xs leading-6 text-cyan-100">
                {prettyJson(detail.toolEventsJson)}
              </pre>
            </Panel>
          </div>
        </>
      ) : (
        <Panel title="Trace 详情" eyebrow="Detail">
          <EmptyState text="先从右侧选择一个 Trace。" />
        </Panel>
      )}
    </WorkspaceShell>
  );
}

function EmptyState({ text }: { text: string }) {
  return (
    <div className="rounded-[1.3rem] border border-dashed border-[var(--line-soft)] bg-[rgba(255,255,255,0.68)] px-4 py-8 text-sm leading-7 text-[var(--ink-subtle)]">
      {text}
    </div>
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
            <div className="grid gap-5 xl:grid-cols-2">
              <div className="h-48 animate-pulse rounded-[1.75rem] bg-slate-100" />
              <div className="h-48 animate-pulse rounded-[1.75rem] bg-slate-100" />
            </div>
          </div>
          <div className="h-[calc(100vh-2.5rem)] animate-pulse rounded-[1.75rem] bg-slate-100" />
        </div>
      </div>
    </main>
  );
}
