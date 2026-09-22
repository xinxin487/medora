"use client";

import Link from "next/link";
import { FormEvent, startTransition, useEffect, useState } from "react";
import { apiFetch } from "@/lib/api/client";
import { useAuth } from "@/lib/hooks/use-auth";
import { sendChatMessageStream } from "@/lib/sse/chat-stream";
import { reduceA2uiSurface } from "@/lib/a2ui/reducer";
import { loadPendingA2uiSurfaces } from "@/lib/a2ui/action-client";
import type { A2uiSurface } from "@/lib/a2ui/types";
import { A2uiSurfaceCard } from "@/components/a2ui/a2ui-surface";
import type { PageResponse } from "@/lib/types";
import type {
  Conversation,
  ChatMessage,
  ChatCitation,
  ToolStatusData,
} from "@/lib/types/api";
import { WorkspaceShell } from "@/components/workspace-shell";
import { Panel } from "@/components/panel";
import { Badge } from "@/components/badge";
import { Markdown } from "@/components/markdown";

const quickPrompts = [
  "我最近咳嗽，应该挂哪个科？",
  "帮我看看明天下午还有没有号源。",
  "我已经预约了，下次复诊前需要注意什么？",
];

const quickActions = [
  { label: "查看周三上午号源", prompt: "帮我查查周三上午还有没有号源" },
  { label: "显示我的预约记录", prompt: "帮我查一下我的预约记录" },
  { label: "挂号前准备事项", prompt: "就诊前需要准备什么？" },
  { label: "如何取消预约", prompt: "我想取消一个已有的预约，怎么操作？" },
];

export default function AssistantPage() {
  const { isLoading: authLoading } = useAuth("USER");

  const [conversations, setConversations] = useState<Conversation[]>([]);
  const [activeConversationId, setActiveConversationId] = useState("");
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [draft, setDraft] = useState("");
  const [status, setStatus] = useState(
    "在这里继续你的导诊和挂号流程。",
  );
  const [error, setError] = useState<string | null>(null);
  const [isStreaming, setIsStreaming] = useState(false);
  const [a2uiSurfaces, setA2uiSurfaces] = useState<Record<string, A2uiSurface>>({});

  const activeConversation =
    conversations.find(
      (c) => c.conversationId === activeConversationId,
    ) ?? null;

  // Collect citations from latest assistant message for the aside panel
  const latestAssistantMsg = [...messages]
    .reverse()
    .find((m) => m.role === "ASSISTANT");
  const latestCitations = latestAssistantMsg
    ? parseCitations(latestAssistantMsg.citations)
    : [];

  useEffect(() => {
    void loadConversations();
  }, []);

  useEffect(() => {
    if (activeConversationId) {
      void loadMessages(activeConversationId);
      void loadPendingSurfaces(activeConversationId);
    } else {
      setA2uiSurfaces({});
    }
  }, [activeConversationId]);

  async function loadConversations() {
    await runAction(async () => {
      const result = await apiFetch<PageResponse<Conversation>>(
        "/chat/conversations?pageSize=20",
      );
      setConversations(result.list);
      if (!activeConversationId && result.list[0]) {
        startTransition(() =>
          setActiveConversationId(result.list[0].conversationId),
        );
      }
    }, false);
  }

  async function deleteConversation(conversationId: string) {
    if (isStreaming) return;
    if (!window.confirm("确认删除这个会话?删除后不在列表中展示,但审计记录保留。")) {
      return;
    }
    await runAction(async () => {
      await apiFetch(`/chat/conversations/${conversationId}`, {
        method: "DELETE",
      });
      setConversations((cur) =>
        cur.filter((c) => c.conversationId !== conversationId),
      );
      if (activeConversationId === conversationId) {
        startTransition(() => setActiveConversationId(""));
        setMessages([]);
        setStatus("会话已删除,请新建或选择其他会话。");
      }
    });
  }

  async function createConversation() {
    await runAction(async () => {
      const conv = await apiFetch<Conversation>("/chat/conversations", {
        method: "POST",
        body: JSON.stringify({ title: "导诊咨询", sceneType: "GENERAL" }),
      });
      setConversations((cur) => [conv, ...cur]);
      startTransition(() => setActiveConversationId(conv.conversationId));
      setMessages([]);
      setStatus("新会话已就绪，可以开始问诊。");
    }, false);
  }

  async function ensureConversation() {
    if (activeConversationId) return activeConversationId;
    const conv = await apiFetch<Conversation>("/chat/conversations", {
      method: "POST",
      body: JSON.stringify({ title: "导诊咨询", sceneType: "GENERAL" }),
    });
    setConversations((cur) => [conv, ...cur]);
    startTransition(() => setActiveConversationId(conv.conversationId));
    return conv.conversationId;
  }

  async function loadMessages(conversationId: string) {
    await runAction(async () => {
      const result = await apiFetch<PageResponse<ChatMessage>>(
        `/chat/conversations/${conversationId}/messages?pageSize=100`,
      );
      setMessages(result.list);
      setStatus("会话记录已加载。");
    }, false);
  }

  async function loadPendingSurfaces(conversationId: string) {
    await runAction(async () => {
      const surfaces = await loadPendingA2uiSurfaces(conversationId);
      setA2uiSurfaces(
        surfaces.reduce<Record<string, A2uiSurface>>(
          (current, surface) => reduceA2uiSurface(current, surface),
          {},
        ),
      );
    }, false);
  }

  async function sendMessage(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const content = draft.trim();
    if (!content || isStreaming) return;

    setError(null);
    setIsStreaming(true);
    setDraft("");

    const localUserId = `local-user-${crypto.randomUUID()}`;
    const localAssistantId = `local-assistant-${crypto.randomUUID()}`;
    setMessages((cur) => [
      ...cur,
      {
        messageId: localUserId,
        role: "USER",
        messageType: "TEXT",
        content,
        citations: "[]",
        createdAt: new Date().toISOString(),
        pending: true,
      },
      {
        messageId: localAssistantId,
        role: "ASSISTANT",
        messageType: "TEXT",
        content: "",
        citations: "[]",
        createdAt: new Date().toISOString(),
        pending: true,
      },
    ]);

    try {
      const conversationId = await ensureConversation();
      await sendChatMessageStream({
        conversationId,
        message: content,
        clientMessageId: localUserId,
        sceneType: activeConversation?.sceneType ?? "GENERAL",
        onEvent: (streamEvent) => {
          if (streamEvent.event === "message_start") {
            setStatus("助手正在生成回复...");
            setMessages((cur) =>
              cur.map((m) =>
                m.messageId === localAssistantId
                  ? { ...m, messageId: streamEvent.data.messageId }
                  : m,
              ),
            );
          }
          if (streamEvent.event === "message_delta") {
            setMessages((cur) =>
              cur.map((m) =>
                m.messageId === localAssistantId ||
                (m.pending && m.role === "ASSISTANT")
                  ? { ...m, content: `${m.content}${streamEvent.data.delta}` }
                  : m,
              ),
            );
          }
          if (streamEvent.event === "message_end") {
            setStatus("回复完成，可以继续提问。");
          }
          if (streamEvent.event === "citation") {
            setMessages((cur) =>
              cur.map((m) => {
                if (m.role !== "ASSISTANT" || !m.pending) return m;
                const citations = parseCitations(m.citations);
                return {
                  ...m,
                  citations: JSON.stringify([...citations, streamEvent.data]),
                };
              }),
            );
          }
          if (streamEvent.event === "tool_status") {
            const ts = streamEvent.data as ToolStatusData;
            setStatus(ts.message ?? "工具执行中...");
          }
          if (streamEvent.event === "a2ui") {
            setA2uiSurfaces((current) => reduceA2uiSurface(current, streamEvent.data));
          }
          if (streamEvent.event === "error") {
            setError(
              `${streamEvent.data.code}: ${streamEvent.data.message}`,
            );
          }
        },
      });
      await loadMessages(conversationId);
      await loadConversations();
    } catch (cause) {
      if (cause instanceof Error) {
        setError(cause.message);
      } else {
        setError("对话请求失败，请检查网络后重试。");
      }
    } finally {
      setIsStreaming(false);
    }
  }

  async function runAction(
    action: () => Promise<void>,
    resetError = true,
  ) {
    if (resetError) setError(null);
    try {
      await action();
    } catch (cause) {
      if (cause instanceof Error) {
        setError(cause.message);
      } else {
        setError("请求失败，请检查网络后重试。");
      }
    }
  }

  if (authLoading) return <LoadingSkeleton />;

  return (
    <WorkspaceShell
      role="patient"
      activeKey="assistant"
      title="导诊助手"
      subtitle="会话、知识依据与后续服务动作放在同一工作区。"
      actions={
        <>
          <button
            className="rounded-full bg-[var(--accent-main)] px-5 py-3 text-sm font-semibold text-white transition hover:bg-[var(--accent-main-strong)] disabled:opacity-60"
            disabled={isStreaming}
            onClick={() => void createConversation()}
            type="button"
          >
            新建会话
          </button>
          <Link
            href="/app/appointments"
            className="rounded-full border border-[var(--line-soft)] bg-white px-5 py-3 text-sm font-semibold text-[var(--ink-strong)] transition hover:border-[var(--accent-main)] hover:text-[var(--accent-main)]"
          >
            去预约服务
          </Link>
        </>
      }
      aside={
        <>
          <Panel title="知识依据" eyebrow="References">
            {latestCitations.length > 0 ? (
              <div className="space-y-3">
                {latestCitations.map((cit, i) => (
                  <div
                    key={`${cit.sourceId ?? i}`}
                    className="rounded-[1.3rem] border border-[var(--line-soft)] bg-white/82 p-3"
                  >
                    <p className="text-xs font-semibold text-[var(--accent-main)]">
                      [{cit.index ?? i + 1}] {cit.title ?? "知识库引用"}
                    </p>
                    <p className="mt-1 text-xs leading-5 text-[var(--ink-body)]">
                      {cit.snippet}
                    </p>
                  </div>
                ))}
              </div>
            ) : (
              <div className="rounded-[1.3rem] border border-dashed border-[var(--line-soft)] bg-[rgba(255,255,255,0.68)] px-4 py-6 text-sm text-[var(--ink-subtle)]">
                对话中引用的知识将在这里展示。
              </div>
            )}
          </Panel>

          <Panel title="快捷动作" eyebrow="Quick Actions">
            <div className="flex flex-wrap gap-2">
              {quickActions.map((qa) => (
                <button
                  key={qa.label}
                  type="button"
                  onClick={() => setDraft(qa.prompt)}
                  className="rounded-full border border-[var(--line-soft)] bg-white/80 px-3 py-2 text-xs font-semibold text-[var(--ink-body)] transition hover:border-[var(--accent-main)] hover:text-[var(--accent-main)]"
                >
                  {qa.label}
                </button>
              ))}
            </div>
          </Panel>

          <Panel title="下一步建议" eyebrow="Next Step">
            <div className="rounded-[1.3rem] border border-[rgba(31,92,82,0.14)] bg-[rgba(31,92,82,0.06)] px-4 py-4 text-sm leading-7 text-[var(--ink-body)]">
              如果你已经确定就诊方向，可以直接切到预约中心查看实时号源。
            </div>
            <div className="mt-3">
              <Link
                href="/app/appointments"
                className="block rounded-full bg-[var(--accent-main)] px-4 py-2.5 text-center text-sm font-semibold text-white"
              >
                去预约服务
              </Link>
            </div>
          </Panel>
        </>
      }
    >
      {/* ── Conversation strip ── */}
      <div className="flex items-center gap-2 overflow-x-auto pb-1">
        {conversations.map((conv) => {
          const isActive = conv.conversationId === activeConversationId;
          return (
            <div
              key={conv.conversationId}
              className={`group flex shrink-0 items-center rounded-full border transition ${
                isActive
                  ? "border-[var(--accent-main)] bg-[var(--accent-main)] text-white"
                  : "border-[var(--line-soft)] bg-white/80 text-[var(--ink-body)] hover:border-[var(--accent-main)]"
              }`}
            >
              <button
                type="button"
                disabled={isStreaming}
                onClick={() =>
                  startTransition(() =>
                    setActiveConversationId(conv.conversationId),
                  )
                }
                className="rounded-l-full px-4 py-2 text-xs font-semibold"
              >
                {conv.title}
              </button>
              <button
                type="button"
                disabled={isStreaming}
                onClick={() => void deleteConversation(conv.conversationId)}
                title="删除会话"
                aria-label={`删除会话 ${conv.title}`}
                className={`rounded-r-full px-2 py-2 text-xs transition ${
                  isActive
                    ? "text-white/70 hover:bg-white/20 hover:text-white"
                    : "text-[var(--ink-subtle)] hover:bg-[var(--danger-soft)] hover:text-[#8a4a3a]"
                }`}
              >
                ×
              </button>
            </div>
          );
        })}
        <button
          type="button"
          disabled={isStreaming}
          onClick={() => void createConversation()}
          className="shrink-0 rounded-full border border-dashed border-[var(--line-soft)] px-4 py-2 text-xs font-semibold text-[var(--ink-subtle)] transition hover:border-[var(--accent-main)] hover:text-[var(--accent-main)]"
        >
          + 新建会话
        </button>
      </div>

      {/* ── Chat area ── */}
      <section className="medora-surface medora-grid border border-white/60">
        <header className="border-b border-[rgba(20,42,39,0.08)] px-5 py-4">
          <div className="flex items-center justify-between gap-3">
            <div>
              <h2 className="text-lg font-semibold text-[var(--ink-strong)]">
                {activeConversation?.title ?? "新的导诊会话"}
              </h2>
              <p className="mt-1 text-sm text-[var(--ink-body)]">{status}</p>
            </div>
            {isStreaming && <Badge tone="soft">生成中</Badge>}
          </div>
          {error ? (
            <div className="mt-3 rounded-[1.2rem] border border-[rgba(190,92,67,0.18)] bg-[var(--danger-soft)] px-4 py-3 text-sm leading-6 text-[#8a4a3a]">
              {error}
            </div>
          ) : null}
        </header>

        <div className="min-h-[420px] space-y-4 overflow-y-auto px-5 py-5">
          {Object.values(a2uiSurfaces).map((surface) => (
            <A2uiSurfaceCard key={surface.surfaceId} surface={surface}
              onResolved={(surfaceId) => setA2uiSurfaces((current) => reduceA2uiSurface(current, {
                event: "deleteSurface", surfaceId, rootId: "", components: [], dataModel: {},
              }))} />
          ))}
          {messages.length === 0 ? (
            <div className="space-y-4">
              <div className="rounded-[1.5rem] border border-[rgba(20,42,39,0.08)] bg-[rgba(237,243,238,0.6)] px-5 py-6">
                <p className="medora-display text-2xl font-semibold">
                  从这里开始你的医疗问诊对话。
                </p>
                <p className="mt-3 text-sm leading-7 text-[var(--ink-body)]">
                  你可以直接问挂什么科、是否还有号、怎么取消预约，或者继续追问就诊准备事项。
                </p>
              </div>
              <div className="flex flex-wrap gap-2">
                {quickPrompts.map((prompt) => (
                  <button
                    key={prompt}
                    type="button"
                    onClick={() => setDraft(prompt)}
                    className="rounded-full border border-[var(--line-soft)] bg-white/80 px-4 py-2 text-sm font-semibold text-[var(--ink-body)] transition hover:border-[var(--accent-main)] hover:text-[var(--accent-main)]"
                  >
                    {prompt}
                  </button>
                ))}
              </div>
            </div>
          ) : (
            messages.map((msg) => (
              <article
                key={msg.messageId}
                className={`flex ${msg.role === "USER" ? "justify-end" : "justify-start"}`}
              >
                <div
                  className={`max-w-[82%] rounded-[1.4rem] px-4 py-4 text-sm leading-7 ${
                    msg.role === "USER"
                      ? "bg-[var(--ink-strong)] text-white shadow-[0_18px_36px_rgba(20,42,39,0.14)]"
                      : "border border-[rgba(20,42,39,0.08)] bg-[rgba(237,243,238,0.58)] text-[var(--ink-strong)]"
                  }`}
                >
                  <div className="mb-2 text-xs font-semibold uppercase tracking-[0.22em] opacity-70">
                    {msg.role === "USER" ? "You" : "Medora"}
                  </div>
                  {msg.role === "ASSISTANT" && msg.content ? (
                    <Markdown content={msg.content} />
                  ) : (
                    msg.content || (msg.pending ? "正在生成..." : "")
                  )}
                  {msg.role === "ASSISTANT" &&
                    parseCitations(msg.citations).length > 0 && (
                      <div className="mt-4 space-y-2 border-t border-[rgba(20,42,39,0.1)] pt-3">
                        {parseCitations(msg.citations).map((cit, i) => (
                          <div
                            key={`${msg.messageId}-cit-${cit.sourceId ?? i}`}
                            className="rounded-[1.1rem] border border-[rgba(20,42,39,0.08)] bg-white/88 px-3 py-2 text-xs leading-5 text-[var(--ink-body)]"
                          >
                            <span className="font-semibold text-[var(--accent-main)]">
                              [{cit.index ?? i + 1}] {cit.title ?? "引用"}
                            </span>
                            <span className="ml-1">{cit.snippet}</span>
                          </div>
                        ))}
                      </div>
                    )}
                </div>
              </article>
            ))
          )}
        </div>

        <form
          className="border-t border-[rgba(20,42,39,0.08)] p-4"
          onSubmit={sendMessage}
        >
          <div className="grid gap-3 sm:grid-cols-[1fr_auto]">
            <textarea
              className="min-h-24 resize-none rounded-[1.2rem] border border-[var(--line-soft)] bg-white/84 px-4 py-3 text-sm leading-7 text-[var(--ink-strong)] outline-none transition focus:border-[var(--accent-main)]"
              disabled={isStreaming}
              onChange={(e) => setDraft(e.target.value)}
              value={draft}
            />
            <button
              className="rounded-[1.2rem] bg-[var(--accent-main)] px-6 py-4 text-sm font-semibold text-white disabled:opacity-60"
              disabled={isStreaming || !draft.trim()}
              type="submit"
            >
              {isStreaming ? "发送中" : "发送"}
            </button>
          </div>
        </form>
      </section>
    </WorkspaceShell>
  );
}

function parseCitations(value: string): ChatCitation[] {
  try {
    const parsed = JSON.parse(value);
    return Array.isArray(parsed) ? parsed : [];
  } catch {
    return [];
  }
}

function LoadingSkeleton() {
  return (
    <main className="min-h-screen px-4 py-5 sm:px-6">
      <div className="mx-auto max-w-[1520px]">
        <div className="grid gap-5 xl:grid-cols-[280px_minmax(0,1fr)_320px]">
          <div className="h-[calc(100vh-2.5rem)] animate-pulse rounded-[2rem] bg-slate-200" />
          <div className="space-y-5">
            <div className="h-48 animate-pulse rounded-[1.75rem] bg-slate-100" />
            <div className="h-[420px] animate-pulse rounded-[1.75rem] bg-slate-100" />
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
