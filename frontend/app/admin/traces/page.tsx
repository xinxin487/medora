"use client";

import { startTransition, useEffect, useState } from "react";
import { AdminShell } from "@/components/admin-shell";
import { ApiClientError, apiFetch } from "@/lib/api/client";
import type { PageResponse } from "@/lib/types";

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
  const [traceId, setTraceId] = useState("");
  const [conversationId, setConversationId] = useState("");
  const [status, setStatus] = useState("");
  const [pageNo, setPageNo] = useState(1);
  const [page, setPage] = useState<PageResponse<TraceItem> | null>(null);
  const [selectedTraceId, setSelectedTraceId] = useState<string | null>(null);
  const [detail, setDetail] = useState<TraceDetail | null>(null);
  const [loading, setLoading] = useState(false);
  const [detailLoading, setDetailLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    void loadList(pageNo);
  }, [pageNo]);

  useEffect(() => {
    const params = new URLSearchParams(window.location.search);
    const nextConversationId = params.get("conversationId");
    if (nextConversationId) {
      setConversationId(nextConversationId);
    }
  }, []);

  useEffect(() => {
    if (!selectedTraceId) {
      return;
    }
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
      handleError(cause, "加载 Trace 列表失败。");
    } finally {
      setLoading(false);
    }
  }

  async function loadDetail(targetTraceId: string) {
    setDetailLoading(true);
    setError(null);
    try {
      const result = await apiFetch<TraceDetail>(`/admin/agent-traces/${targetTraceId}`);
      setDetail(result);
    } catch (cause) {
      handleError(cause, "加载 Trace 详情失败。");
    } finally {
      setDetailLoading(false);
    }
  }

  function handleError(cause: unknown, fallback: string) {
    if (cause instanceof ApiClientError) {
      setError(`${cause.code}: ${cause.message}`);
      return;
    }
    setError(fallback);
  }

  return (
    <AdminShell
      title="Agent Trace"
      description="这里集中查看一次对话请求的路由决策、工具事件、检索结果和耗时，适合定位工具边界、检索噪音和失败回退。"
    >
      <div className="grid gap-5 xl:grid-cols-[360px_1fr]">
        <aside className="space-y-4">
          <form
            className="rounded-[1.5rem] border border-slate-200 bg-white p-4"
            onSubmit={(event) => {
              event.preventDefault();
              setPageNo(1);
              void loadList(1);
            }}
          >
            <h2 className="text-base font-semibold">筛选条件</h2>
            <div className="mt-3 space-y-3">
              <input
                className="w-full rounded-xl border border-slate-200 px-3 py-2 text-sm"
                placeholder="traceId"
                value={traceId}
                onChange={(event) => setTraceId(event.target.value)}
              />
              <input
                className="w-full rounded-xl border border-slate-200 px-3 py-2 text-sm"
                placeholder="conversationId"
                value={conversationId}
                onChange={(event) => setConversationId(event.target.value)}
              />
              <select
                className="w-full rounded-xl border border-slate-200 px-3 py-2 text-sm"
                value={status}
                onChange={(event) => setStatus(event.target.value)}
              >
                <option value="">全部状态</option>
                <option value="SUCCESS">SUCCESS</option>
                <option value="FAILED">FAILED</option>
              </select>
              <button
                className="w-full rounded-xl bg-cyan-700 px-4 py-2 text-sm font-semibold text-white disabled:opacity-60"
                disabled={loading}
                type="submit"
              >
                {loading ? "加载中..." : "刷新列表"}
              </button>
            </div>
          </form>

          <div className="rounded-[1.5rem] border border-slate-200 bg-white">
            <div className="border-b border-slate-200 px-4 py-3">
              <div className="flex items-center justify-between gap-3">
                <h2 className="text-base font-semibold">Trace 列表</h2>
                <span className="text-xs text-slate-500">{page?.total ?? 0} 条</span>
              </div>
            </div>
            <div className="divide-y divide-slate-100">
              {page?.list.length ? (
                page.list.map((item) => (
                  <button
                    key={item.traceId}
                    type="button"
                    onClick={() => startTransition(() => setSelectedTraceId(item.traceId))}
                    className={`w-full px-4 py-4 text-left transition ${
                      item.traceId === selectedTraceId ? "bg-cyan-50" : "hover:bg-slate-50"
                    }`}
                  >
                    <div className="flex items-center justify-between gap-3">
                      <div>
                        <h3 className="font-semibold">{item.intentType}</h3>
                        <p className="mt-1 text-xs text-slate-500">
                          {item.routeMode} · {item.latencyMs} ms
                        </p>
                      </div>
                      <span className="rounded-full bg-slate-100 px-2 py-1 text-xs font-semibold text-slate-600">
                        {item.status}
                      </span>
                    </div>
                    <p className="mt-2 line-clamp-2 text-xs leading-6 text-slate-500">{item.traceId}</p>
                  </button>
                ))
              ) : (
                <div className="px-4 py-8 text-sm text-slate-500">暂无 Trace 数据。</div>
              )}
            </div>
            <div className="flex items-center justify-between border-t border-slate-200 px-4 py-3">
              <button
                className="rounded-full border border-slate-200 px-3 py-1 text-sm disabled:opacity-50"
                disabled={pageNo <= 1 || loading}
                onClick={() => setPageNo((current) => Math.max(current - 1, 1))}
                type="button"
              >
                上一页
              </button>
              <span className="text-sm text-slate-500">第 {pageNo} 页</span>
              <button
                className="rounded-full border border-slate-200 px-3 py-1 text-sm disabled:opacity-50"
                disabled={!page?.hasNext || loading}
                onClick={() => setPageNo((current) => current + 1)}
                type="button"
              >
                下一页
              </button>
            </div>
          </div>
        </aside>

        <section className="space-y-5">
          {error ? (
            <div className="rounded-[1.5rem] border border-rose-200 bg-rose-50 px-4 py-3 text-sm leading-6 text-rose-700">
              {error}
            </div>
          ) : null}

          {detail ? (
            <>
              <div className="rounded-[1.5rem] border border-slate-200 bg-white p-5">
                <div className="flex flex-wrap items-start justify-between gap-4">
                  <div>
                    <h2 className="text-xl font-semibold">{detail.intentType}</h2>
                    <p className="mt-2 text-sm leading-6 text-slate-600">
                      {detail.routeMode} · {detail.status} · {detail.latencyMs} ms
                    </p>
                  </div>
                  <div className="rounded-2xl bg-slate-50 px-4 py-3 text-sm leading-7 text-slate-600">
                    <div>创建时间：{formatTime(detail.createdAt)}</div>
                    <div>会话：{detail.conversationId}</div>
                    <div>消息：{detail.messageId ?? "无"}</div>
                  </div>
                </div>
                {detail.errorMessage ? (
                  <div className="mt-4 rounded-2xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm leading-6 text-rose-700">
                    {detail.errorMessage}
                  </div>
                ) : null}
              </div>

              <div className="grid gap-5 xl:grid-cols-2">
                <article className="rounded-[1.5rem] border border-slate-200 bg-white p-5">
                  <h3 className="font-semibold">检索结果 JSON</h3>
                  <pre className="mt-4 overflow-x-auto rounded-2xl bg-slate-950 p-4 text-xs leading-6 text-emerald-100">
                    {prettyJson(detail.retrievalJson)}
                  </pre>
                </article>
                <article className="rounded-[1.5rem] border border-slate-200 bg-white p-5">
                  <h3 className="font-semibold">工具事件 JSON</h3>
                  <pre className="mt-4 overflow-x-auto rounded-2xl bg-slate-950 p-4 text-xs leading-6 text-cyan-100">
                    {prettyJson(detail.toolEventsJson)}
                  </pre>
                </article>
              </div>
            </>
          ) : (
            <div className="rounded-[1.5rem] border border-slate-200 bg-white px-5 py-8 text-sm text-slate-500">
              {detailLoading ? "正在加载详情..." : "先从左侧选择一个 Trace。"}
            </div>
          )}
        </section>
      </div>
    </AdminShell>
  );
}
