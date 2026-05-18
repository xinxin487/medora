"use client";

import Link from "next/link";
import { startTransition, useEffect, useState } from "react";
import { AdminShell } from "@/components/admin-shell";
import { ApiClientError, apiFetch } from "@/lib/api/client";
import type { PageResponse } from "@/lib/types";

type ConversationItem = {
  conversationId: string;
  title: string;
  sceneType: string;
  status: string;
  userId: string;
  userLabel: string;
  messageCount: number;
  summaryText: string;
  lastMessageAt: string | null;
  createdAt: string;
};

type MemoryItem = {
  memoryId: string;
  memoryType: string;
  memoryKey: string;
  summary: string;
  valueJson: string;
  confidence: number;
  source: string;
  status: string;
  lastConfirmedAt: string;
  lastAccessedAt: string | null;
  expiresAt: string | null;
};

type ConversationDetail = {
  conversationId: string;
  title: string;
  sceneType: string;
  status: string;
  userId: string;
  userLabel: string;
  messageCount: number;
  createdAt: string;
  lastMessageAt: string | null;
  summaryText: string;
  summaryJson: string;
  businessStateJson: string;
  lastSummarizedAt: string | null;
  memories: MemoryItem[];
};

type MessageItem = {
  messageId: string;
  role: string;
  messageType: string;
  content: string;
  citationJson: string;
  toolCallJson: string;
  tokenUsageJson: string;
  createdAt: string;
};

function formatTime(value: string | null) {
  if (!value) {
    return "暂无";
  }
  return new Date(value).toLocaleString("zh-CN", { hour12: false });
}

function prettyJson(value: string) {
  try {
    return JSON.stringify(JSON.parse(value), null, 2);
  } catch {
    return value;
  }
}

export default function AdminConversationsPage() {
  const [keyword, setKeyword] = useState("");
  const [status, setStatus] = useState("");
  const [pageNo, setPageNo] = useState(1);
  const [page, setPage] = useState<PageResponse<ConversationItem> | null>(null);
  const [selectedConversationId, setSelectedConversationId] = useState<string | null>(null);
  const [detail, setDetail] = useState<ConversationDetail | null>(null);
  const [messages, setMessages] = useState<MessageItem[]>([]);
  const [loading, setLoading] = useState(false);
  const [detailLoading, setDetailLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    void loadConversations(pageNo);
  }, [pageNo]);

  useEffect(() => {
    if (!selectedConversationId) {
      return;
    }
    void loadConversationDetail(selectedConversationId);
  }, [selectedConversationId]);

  async function loadConversations(targetPage: number) {
    setLoading(true);
    setError(null);
    try {
      const result = await apiFetch<PageResponse<ConversationItem>>(
        `/admin/conversations?pageNo=${targetPage}&pageSize=20&keyword=${encodeURIComponent(keyword)}&status=${encodeURIComponent(status)}`,
      );
      setPage(result);
      if (!selectedConversationId && result.list.length > 0) {
        startTransition(() => setSelectedConversationId(result.list[0].conversationId));
      }
    } catch (cause) {
      handleError(cause, "加载会话列表失败，请确认管理员权限是否可用。");
    } finally {
      setLoading(false);
    }
  }

  async function loadConversationDetail(conversationId: string) {
    setDetailLoading(true);
    setError(null);
    try {
      const [detailResult, messageResult] = await Promise.all([
        apiFetch<ConversationDetail>(`/admin/conversations/${conversationId}`),
        apiFetch<PageResponse<MessageItem>>(`/admin/conversations/${conversationId}/messages?pageSize=100`),
      ]);
      setDetail(detailResult);
      setMessages(messageResult.list);
    } catch (cause) {
      handleError(cause, "加载会话详情失败。");
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
      title="会话查看"
      description="这里聚合了会话列表、摘要、长期记忆和完整消息流，适合排查上下文裁剪、记忆污染和业务连续性问题。"
      actions={
        detail ? (
          <Link
            className="rounded-full bg-slate-950 px-4 py-2 text-sm font-semibold text-white"
            href={`/admin/traces?conversationId=${detail.conversationId}`}
          >
            查看该会话 Trace
          </Link>
        ) : null
      }
    >
      <div className="grid gap-5 xl:grid-cols-[360px_1fr]">
        <aside className="space-y-4">
          <form
            className="rounded-[1.5rem] border border-slate-200 bg-white p-4"
            onSubmit={(event) => {
              event.preventDefault();
              setPageNo(1);
              void loadConversations(1);
            }}
          >
            <h2 className="text-base font-semibold">筛选条件</h2>
            <div className="mt-3 space-y-3">
              <input
                className="w-full rounded-xl border border-slate-200 px-3 py-2 text-sm"
                placeholder="会话标题 / 用户名 / 手机 / 邮箱"
                value={keyword}
                onChange={(event) => setKeyword(event.target.value)}
              />
              <select
                className="w-full rounded-xl border border-slate-200 px-3 py-2 text-sm"
                value={status}
                onChange={(event) => setStatus(event.target.value)}
              >
                <option value="">全部状态</option>
                <option value="ACTIVE">ACTIVE</option>
              </select>
              <button
                className="w-full rounded-xl bg-emerald-700 px-4 py-2 text-sm font-semibold text-white disabled:opacity-60"
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
                <h2 className="text-base font-semibold">会话列表</h2>
                <span className="text-xs text-slate-500">{page?.total ?? 0} 条</span>
              </div>
            </div>
            <div className="divide-y divide-slate-100">
              {page?.list.length ? (
                page.list.map((item) => {
                  const active = item.conversationId === selectedConversationId;
                  return (
                    <button
                      key={item.conversationId}
                      type="button"
                      onClick={() => startTransition(() => setSelectedConversationId(item.conversationId))}
                      className={`w-full px-4 py-4 text-left transition ${
                        active ? "bg-emerald-50" : "hover:bg-slate-50"
                      }`}
                    >
                      <div className="flex items-start justify-between gap-3">
                        <div>
                          <h3 className="font-semibold">{item.title}</h3>
                          <p className="mt-1 text-xs text-slate-500">
                            {item.userLabel} · {item.sceneType} · {item.messageCount} 条消息
                          </p>
                        </div>
                        <span className="rounded-full bg-slate-100 px-2 py-1 text-xs font-semibold text-slate-600">
                          {item.status}
                        </span>
                      </div>
                      <p className="mt-2 line-clamp-3 text-sm leading-6 text-slate-600">
                        {item.summaryText || "暂无摘要。"}
                      </p>
                    </button>
                  );
                })
              ) : (
                <div className="px-4 py-8 text-sm text-slate-500">暂无会话数据。</div>
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
                    <h2 className="text-xl font-semibold">{detail.title}</h2>
                    <p className="mt-2 text-sm leading-6 text-slate-600">
                      {detail.userLabel} · {detail.sceneType} · {detail.messageCount} 条消息
                    </p>
                  </div>
                  <div className="rounded-2xl bg-slate-50 px-4 py-3 text-sm leading-7 text-slate-600">
                    <div>创建时间：{formatTime(detail.createdAt)}</div>
                    <div>最后消息：{formatTime(detail.lastMessageAt)}</div>
                    <div>最近摘要：{formatTime(detail.lastSummarizedAt)}</div>
                  </div>
                </div>

                <div className="mt-5 grid gap-4 xl:grid-cols-2">
                  <article className="rounded-[1.25rem] border border-slate-200 bg-slate-50 p-4">
                    <h3 className="font-semibold">会话摘要</h3>
                    <p className="mt-3 text-sm leading-7 text-slate-700">
                      {detail.summaryText || "暂无摘要内容。"}
                    </p>
                    <pre className="mt-4 overflow-x-auto rounded-2xl bg-slate-950 p-4 text-xs leading-6 text-emerald-100">
                      {prettyJson(detail.summaryJson)}
                    </pre>
                  </article>

                  <article className="rounded-[1.25rem] border border-slate-200 bg-slate-50 p-4">
                    <h3 className="font-semibold">业务状态</h3>
                    <pre className="mt-4 overflow-x-auto rounded-2xl bg-slate-950 p-4 text-xs leading-6 text-cyan-100">
                      {prettyJson(detail.businessStateJson)}
                    </pre>
                  </article>
                </div>
              </div>

              <div className="grid gap-5 xl:grid-cols-[0.9fr_1.1fr]">
                <div className="rounded-[1.5rem] border border-slate-200 bg-white">
                  <div className="border-b border-slate-200 px-4 py-3">
                    <h2 className="text-base font-semibold">长期记忆</h2>
                  </div>
                  <div className="divide-y divide-slate-100">
                    {detail.memories.length ? (
                      detail.memories.map((item) => (
                        <article className="px-4 py-4" key={item.memoryId}>
                          <div className="flex flex-wrap items-center gap-2">
                            <h3 className="font-semibold">{item.memoryType}</h3>
                            <span className="rounded-full bg-emerald-50 px-2 py-1 text-xs font-semibold text-emerald-700">
                              {item.confidence}
                            </span>
                          </div>
                          <p className="mt-2 text-sm leading-6 text-slate-700">{item.summary}</p>
                          <pre className="mt-3 overflow-x-auto rounded-2xl bg-slate-950 p-3 text-xs leading-6 text-slate-100">
                            {prettyJson(item.valueJson)}
                          </pre>
                        </article>
                      ))
                    ) : (
                      <div className="px-4 py-8 text-sm text-slate-500">暂无长期记忆。</div>
                    )}
                  </div>
                </div>

                <div className="rounded-[1.5rem] border border-slate-200 bg-white">
                  <div className="border-b border-slate-200 px-4 py-3">
                    <h2 className="text-base font-semibold">消息流</h2>
                  </div>
                  <div className="divide-y divide-slate-100">
                    {detailLoading ? (
                      <div className="px-4 py-8 text-sm text-slate-500">正在加载会话详情...</div>
                    ) : messages.length ? (
                      messages.map((message) => (
                        <article className="px-4 py-4" key={message.messageId}>
                          <div className="flex flex-wrap items-center justify-between gap-3">
                            <div className="flex items-center gap-2">
                              <span
                                className={`rounded-full px-2 py-1 text-xs font-semibold ${
                                  message.role === "ASSISTANT"
                                    ? "bg-cyan-50 text-cyan-700"
                                    : "bg-slate-100 text-slate-700"
                                }`}
                              >
                                {message.role}
                              </span>
                              <span className="text-xs text-slate-500">{formatTime(message.createdAt)}</span>
                            </div>
                          </div>
                          <p className="mt-3 whitespace-pre-wrap text-sm leading-7 text-slate-700">
                            {message.content}
                          </p>
                          <details className="mt-3 rounded-2xl bg-slate-50 p-3">
                            <summary className="cursor-pointer text-sm font-semibold text-slate-700">
                              查看引用 / 工具 / token 预算
                            </summary>
                            <div className="mt-3 grid gap-3">
                              <pre className="overflow-x-auto rounded-2xl bg-slate-950 p-3 text-xs leading-6 text-emerald-100">
                                {prettyJson(message.citationJson)}
                              </pre>
                              <pre className="overflow-x-auto rounded-2xl bg-slate-950 p-3 text-xs leading-6 text-cyan-100">
                                {prettyJson(message.toolCallJson)}
                              </pre>
                              <pre className="overflow-x-auto rounded-2xl bg-slate-950 p-3 text-xs leading-6 text-amber-100">
                                {prettyJson(message.tokenUsageJson)}
                              </pre>
                            </div>
                          </details>
                        </article>
                      ))
                    ) : (
                      <div className="px-4 py-8 text-sm text-slate-500">暂无消息。</div>
                    )}
                  </div>
                </div>
              </div>
            </>
          ) : (
            <div className="rounded-[1.5rem] border border-slate-200 bg-white px-5 py-8 text-sm text-slate-500">
              先从左侧选择一个会话。
            </div>
          )}
        </section>
      </div>
    </AdminShell>
  );
}
