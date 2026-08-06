"use client";

import Link from "next/link";
import { startTransition, useEffect, useState } from "react";
import { apiFetch } from "@/lib/api/client";
import { useAuth } from "@/lib/hooks/use-auth";
import type { PageResponse } from "@/lib/types";
import { WorkspaceShell } from "@/components/workspace-shell";
import { Panel } from "@/components/panel";
import { Badge } from "@/components/badge";

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
  if (!value) return "暂无";
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
  const { isLoading: authLoading } = useAuth("ADMIN");

  const [keyword, setKeyword] = useState("");
  const [status, setStatus] = useState("");
  const [pageNo, setPageNo] = useState(1);
  const [page, setPage] = useState<PageResponse<ConversationItem> | null>(null);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [detail, setDetail] = useState<ConversationDetail | null>(null);
  const [messages, setMessages] = useState<MessageItem[]>([]);
  const [loading, setLoading] = useState(false);
  const [detailLoading, setDetailLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    void loadConversations(pageNo);
  }, [pageNo]);

  useEffect(() => {
    if (!selectedId) return;
    void loadDetail(selectedId);
  }, [selectedId]);

  async function loadConversations(targetPage: number) {
    setLoading(true);
    setError(null);
    try {
      const result = await apiFetch<PageResponse<ConversationItem>>(
        `/admin/conversations?pageNo=${targetPage}&pageSize=20&keyword=${encodeURIComponent(keyword)}&status=${encodeURIComponent(status)}`,
      );
      setPage(result);
      if (!selectedId && result.list.length > 0) {
        startTransition(() => setSelectedId(result.list[0].conversationId));
      }
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "加载会话列表失败。");
    } finally {
      setLoading(false);
    }
  }

  async function loadDetail(conversationId: string) {
    setDetailLoading(true);
    setError(null);
    try {
      const [d, m] = await Promise.all([
        apiFetch<ConversationDetail>(`/admin/conversations/${conversationId}`),
        apiFetch<PageResponse<MessageItem>>(
          `/admin/conversations/${conversationId}/messages?pageSize=100`,
        ),
      ]);
      setDetail(d);
      setMessages(m.list);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "加载会话详情失败。");
    } finally {
      setDetailLoading(false);
    }
  }

  if (authLoading) return <LoadingSkeleton />;

  return (
    <WorkspaceShell
      role="admin"
      activeKey="conversations"
      title="会话查看"
      subtitle="会话列表、摘要、长期记忆和完整消息流。"
      actions={
        detail ? (
          <Link
            className="rounded-full border border-[var(--accent-main)] bg-[rgba(31,92,82,0.08)] px-4 py-2 text-sm font-semibold text-[var(--accent-main)]"
            href={`/admin/traces?conversationId=${detail.conversationId}`}
          >
            查看该会话 Trace
          </Link>
        ) : null
      }
      aside={
        <Panel title="筛选会话" eyebrow="Filters">
          <form
            onSubmit={(e) => {
              e.preventDefault();
              setPageNo(1);
              void loadConversations(1);
            }}
            className="space-y-3"
          >
            <input
              className="w-full rounded-[1.1rem] border border-[var(--line-soft)] bg-white/80 px-3 py-2.5 text-sm outline-none transition focus:border-[var(--accent-main)]"
              placeholder="标题 / 用户名"
              value={keyword}
              onChange={(e) => setKeyword(e.target.value)}
            />
            <select
              className="w-full rounded-[1.1rem] border border-[var(--line-soft)] bg-white/80 px-3 py-2.5 text-sm outline-none transition focus:border-[var(--accent-main)]"
              value={status}
              onChange={(e) => setStatus(e.target.value)}
            >
              <option value="">全部状态</option>
              <option value="ACTIVE">进行中</option>
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
              const active = item.conversationId === selectedId;
              return (
                <button
                  key={item.conversationId}
                  type="button"
                  onClick={() =>
                    startTransition(() => setSelectedId(item.conversationId))
                  }
                  className={`w-full rounded-[1.3rem] border px-3 py-3 text-left transition ${
                    active
                      ? "border-[var(--accent-main)] bg-[rgba(31,92,82,0.08)]"
                      : "border-[var(--line-soft)] bg-white/78 hover:border-[var(--accent-warm)]"
                  }`}
                >
                  <div className="flex items-center justify-between gap-2">
                    <p className="text-sm font-semibold text-[var(--ink-strong)] truncate">
                      {item.title}
                    </p>
                    <Badge>{item.status}</Badge>
                  </div>
                  <p className="mt-1 text-xs text-[var(--ink-subtle)]">
                    {item.userLabel} · {item.messageCount} 条
                  </p>
                </button>
              );
            })}
            {page?.list.length === 0 && (
              <p className="py-4 text-center text-sm text-[var(--ink-subtle)]">
                暂无会话
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
          <Panel title={detail.title} eyebrow="Conversation Detail">
            <div className="flex flex-wrap items-start justify-between gap-4">
              <div>
                <p className="text-sm text-[var(--ink-body)]">
                  {detail.userLabel} · {detail.sceneType} · {detail.messageCount} 条消息
                </p>
                {detail.summaryText && (
                  <p className="mt-3 text-sm leading-7 text-[var(--ink-body)]">
                    {detail.summaryText}
                  </p>
                )}
              </div>
              <div className="rounded-[1.2rem] border border-[var(--line-soft)] bg-white/72 px-4 py-3 text-sm text-[var(--ink-subtle)]">
                <div>创建：{formatTime(detail.createdAt)}</div>
                <div>最后消息：{formatTime(detail.lastMessageAt)}</div>
              </div>
            </div>

            <div className="mt-4 grid gap-4 xl:grid-cols-2">
              <div className="rounded-[1.2rem] border border-[var(--line-soft)] bg-white/72 p-4">
                <p className="text-xs font-semibold uppercase tracking-[0.18em] text-[var(--ink-subtle)]">
                  会话摘要
                </p>
                <pre className="mt-3 overflow-x-auto rounded-2xl bg-[#14362f] p-3 text-xs leading-6 text-emerald-100">
                  {prettyJson(detail.summaryJson)}
                </pre>
              </div>
              <div className="rounded-[1.2rem] border border-[var(--line-soft)] bg-white/72 p-4">
                <p className="text-xs font-semibold uppercase tracking-[0.18em] text-[var(--ink-subtle)]">
                  业务状态
                </p>
                <pre className="mt-3 overflow-x-auto rounded-2xl bg-[#14362f] p-3 text-xs leading-6 text-cyan-100">
                  {prettyJson(detail.businessStateJson)}
                </pre>
              </div>
            </div>
          </Panel>

          <div className="grid gap-5 xl:grid-cols-[0.9fr_1.1fr]">
            <Panel title="长期记忆" eyebrow="Memories">
              {detail.memories.length === 0 ? (
                <EmptyState text="暂无长期记忆。" />
              ) : (
                <div className="space-y-3">
                  {detail.memories.map((mem) => (
                    <div
                      key={mem.memoryId}
                      className="rounded-[1.3rem] border border-[var(--line-soft)] bg-white/84 p-4"
                    >
                      <div className="flex items-center gap-2">
                        <Badge tone="soft">{mem.memoryType}</Badge>
                        <span className="text-xs text-[var(--accent-warm)]">
                          {mem.confidence}
                        </span>
                      </div>
                      <p className="mt-2 text-sm text-[var(--ink-body)]">
                        {mem.summary}
                      </p>
                      <pre className="mt-2 overflow-x-auto rounded-2xl bg-[#14362f] p-3 text-xs leading-6 text-slate-100">
                        {prettyJson(mem.valueJson)}
                      </pre>
                    </div>
                  ))}
                </div>
              )}
            </Panel>

            <Panel title="消息流" eyebrow="Messages">
              {detailLoading ? (
                <p className="py-4 text-sm text-[var(--ink-subtle)]">
                  正在加载...
                </p>
              ) : messages.length === 0 ? (
                <EmptyState text="暂无消息。" />
              ) : (
                <div className="space-y-3">
                  {messages.map((msg) => (
                    <div
                      key={msg.messageId}
                      className="rounded-[1.3rem] border border-[var(--line-soft)] bg-white/84 p-4"
                    >
                      <div className="flex items-center gap-2">
                        <Badge
                          tone={msg.role === "ASSISTANT" ? "soft" : "default"}
                        >
                          {msg.role}
                        </Badge>
                        <span className="text-xs text-[var(--ink-subtle)]">
                          {formatTime(msg.createdAt)}
                        </span>
                      </div>
                      <p className="mt-2 whitespace-pre-wrap text-sm leading-7 text-[var(--ink-body)]">
                        {msg.content}
                      </p>
                      <details className="mt-2">
                        <summary className="cursor-pointer text-xs font-semibold text-[var(--accent-main)]">
                          引用 / 工具调用
                        </summary>
                        <div className="mt-2 grid gap-2">
                          <pre className="overflow-x-auto rounded-2xl bg-[#14362f] p-2 text-xs leading-5 text-emerald-100">
                            {prettyJson(msg.citationJson)}
                          </pre>
                          <pre className="overflow-x-auto rounded-2xl bg-[#14362f] p-2 text-xs leading-5 text-cyan-100">
                            {prettyJson(msg.toolCallJson)}
                          </pre>
                          <pre className="overflow-x-auto rounded-2xl bg-[#14362f] p-2 text-xs leading-5 text-amber-100">
                            {prettyJson(msg.tokenUsageJson)}
                          </pre>
                        </div>
                      </details>
                    </div>
                  ))}
                </div>
              )}
            </Panel>
          </div>
        </>
      ) : (
        <Panel title="会话详情" eyebrow="Detail">
          <EmptyState text="先从右侧选择一个会话。" />
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
            <div className="grid gap-5 xl:grid-cols-[0.9fr_1.1fr]">
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
