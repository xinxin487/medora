"use client";

import Link from "next/link";
import { FormEvent, startTransition, useEffect, useState } from "react";
import { PatientShell } from "@/components/patient-shell";
import { ApiClientError, apiFetch } from "@/lib/api/client";
import { sendChatMessageStream } from "@/lib/sse/chat-stream";
import type { PageResponse } from "@/lib/types";

type Conversation = {
  conversationId: string;
  title: string;
  sceneType: string;
  status: string;
  lastMessageAt: string | null;
};

type ChatMessage = {
  messageId: string;
  role: "USER" | "ASSISTANT";
  messageType: string;
  content: string;
  citations: string;
  createdAt: string;
  pending?: boolean;
};

type ChatCitation = {
  sourceId?: string;
  title?: string;
  snippet?: string;
  index?: number;
};

type ToolStatusData = {
  toolName?: string;
  status?: string;
  message?: string;
};

const quickPrompts = [
  "我最近咳嗽，应该挂哪个科？",
  "帮我看看明天下午还有没有号源。",
  "我已经预约了，下次复诊前需要注意什么？",
];

const assistantCapabilities = [
  "根据问题判断更适合的科室方向",
  "结合知识库引用解释常见挂号与就诊规则",
  "在对话里联动真实号源查询、预约和取消动作",
];

export default function ChatPage() {
  const [conversations, setConversations] = useState<Conversation[]>([]);
  const [activeConversationId, setActiveConversationId] = useState("");
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [draft, setDraft] = useState("我最近咳嗽，应该挂哪个科？");
  const [status, setStatus] = useState("在这里继续你的导诊、挂号和追问流程。");
  const [error, setError] = useState<string | null>(null);
  const [isStreaming, setIsStreaming] = useState(false);

  const activeConversation =
    conversations.find((item) => item.conversationId === activeConversationId) ?? null;

  useEffect(() => {
    void loadConversations();
  }, []);

  useEffect(() => {
    if (activeConversationId) {
      void loadMessages(activeConversationId);
    }
  }, [activeConversationId]);

  async function loadConversations() {
    await runAction(async () => {
      const result = await apiFetch<PageResponse<Conversation>>("/chat/conversations?pageSize=20");
      setConversations(result.list);
      if (!activeConversationId && result.list[0]) {
        startTransition(() => setActiveConversationId(result.list[0].conversationId));
      }
    }, false);
  }

  async function createConversation() {
    await runAction(async () => {
      const conversation = await apiFetch<Conversation>("/chat/conversations", {
        method: "POST",
        body: JSON.stringify({
          title: "导诊咨询",
          sceneType: "GENERAL",
        }),
      });
      setConversations((current) => [conversation, ...current]);
      startTransition(() => setActiveConversationId(conversation.conversationId));
      setMessages([]);
      setStatus("已创建新会话，可以继续问诊或预约。");
    });
  }

  async function ensureConversation() {
    if (activeConversationId) {
      return activeConversationId;
    }
    const conversation = await apiFetch<Conversation>("/chat/conversations", {
      method: "POST",
      body: JSON.stringify({
        title: "导诊咨询",
        sceneType: "GENERAL",
      }),
    });
    setConversations((current) => [conversation, ...current]);
    startTransition(() => setActiveConversationId(conversation.conversationId));
    return conversation.conversationId;
  }

  async function loadMessages(conversationId: string) {
    await runAction(async () => {
      const result = await apiFetch<PageResponse<ChatMessage>>(
        `/chat/conversations/${conversationId}/messages?pageSize=100`,
      );
      setMessages(result.list);
      setStatus(`已载入 ${result.list.length} 条消息。`);
    }, false);
  }

  async function sendMessage(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const content = draft.trim();
    if (!content || isStreaming) {
      return;
    }

    setError(null);
    setIsStreaming(true);
    setDraft("");

    const localUserId = `local-user-${crypto.randomUUID()}`;
    const localAssistantId = `local-assistant-${crypto.randomUUID()}`;
    setMessages((current) => [
      ...current,
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
            setStatus(`助手正在生成回复：${streamEvent.data.traceId}`);
            setMessages((current) =>
              current.map((item) =>
                item.messageId === localAssistantId
                  ? { ...item, messageId: streamEvent.data.messageId }
                  : item,
              ),
            );
          }

          if (streamEvent.event === "message_delta") {
            setMessages((current) =>
              current.map((item) =>
                item.messageId === localAssistantId || (item.pending && item.role === "ASSISTANT")
                  ? { ...item, content: `${item.content}${streamEvent.data.delta}` }
                  : item,
              ),
            );
          }

          if (streamEvent.event === "message_end") {
            setStatus("回复已完成，并已同步到会话历史。");
          }

          if (streamEvent.event === "citation") {
            setMessages((current) =>
              current.map((item) => {
                if (item.role !== "ASSISTANT" || !item.pending) {
                  return item;
                }
                const citations = parseCitations(item.citations);
                return {
                  ...item,
                  citations: JSON.stringify([...citations, streamEvent.data]),
                };
              }),
            );
          }

          if (streamEvent.event === "tool_status") {
            const toolStatus = streamEvent.data as ToolStatusData;
            setStatus(
              `${toolStatus.toolName ?? "tool"} · ${toolStatus.status ?? "RUNNING"} · ${
                toolStatus.message ?? "工具执行中"
              }`,
            );
          }

          if (streamEvent.event === "error") {
            setError(`${streamEvent.data.code}: ${streamEvent.data.message}`);
          }
        },
      });
      await loadMessages(conversationId);
      await loadConversations();
    } catch (cause) {
      if (cause instanceof ApiClientError) {
        setError(`${cause.code}: ${cause.message}`);
      } else {
        setError("对话请求失败，请确认已登录并且后端服务已启动。");
      }
    } finally {
      setIsStreaming(false);
    }
  }

  async function runAction(action: () => Promise<void>, resetError = true) {
    if (resetError) {
      setError(null);
    }
    try {
      await action();
    } catch (cause) {
      if (cause instanceof ApiClientError) {
        setError(`${cause.code}: ${cause.message}`);
      } else {
        setError("请求失败，请确认已登录并且后端服务已启动。");
      }
    }
  }

  return (
    <PatientShell
      eyebrow="Clinical Assistant"
      title="把导诊、知识问答和实际挂号动作，收进同一个连续对话工作台里。"
      description="导诊助手不再只是一个裸聊天框。它会话可追溯、支持引用、可执行真实工具动作，并能与预约流程自然接续。"
      actions={
        <>
          <button
            className="rounded-full bg-[var(--ink-strong)] px-5 py-3 text-sm font-semibold text-white disabled:opacity-60"
            disabled={isStreaming}
            onClick={() => void createConversation()}
            type="button"
          >
            新建会话
          </button>
          <Link
            href="/appointments"
            className="rounded-full border border-[var(--accent-main)] bg-[rgba(31,92,82,0.08)] px-5 py-3 text-sm font-semibold text-[var(--accent-main)]"
          >
            去预约中心
          </Link>
        </>
      }
    >
      <div className="grid gap-5 xl:grid-cols-[280px_1fr_320px]">
        <aside className="medora-surface border border-[var(--line-soft)] px-4 py-5">
          <div className="flex items-center justify-between gap-3">
            <div>
              <p className="text-xs font-semibold uppercase tracking-[0.24em] text-[var(--accent-main)]">
                Conversations
              </p>
              <h2 className="mt-3 text-xl font-semibold">会话列表</h2>
            </div>
            <button
              className="rounded-full border border-[var(--line-soft)] px-3 py-2 text-sm font-semibold text-[var(--ink-body)] disabled:opacity-60"
              disabled={isStreaming}
              onClick={() => void createConversation()}
              type="button"
            >
              新建
            </button>
          </div>

          <div className="mt-5 space-y-2">
            {conversations.length === 0 ? (
              <EmptyState text="还没有会话，先从右侧提问开始。" />
            ) : (
              conversations.map((conversation) => (
                <button
                  key={conversation.conversationId}
                  className={`w-full rounded-[1.2rem] border px-4 py-4 text-left transition ${
                    conversation.conversationId === activeConversationId
                      ? "border-[var(--accent-main)] bg-[rgba(31,92,82,0.08)]"
                      : "border-[rgba(20,42,39,0.08)] bg-white/78 hover:border-[var(--accent-warm)]"
                  }`}
                  disabled={isStreaming}
                  onClick={() => startTransition(() => setActiveConversationId(conversation.conversationId))}
                  type="button"
                >
                  <span className="block font-semibold text-[var(--ink-strong)]">{conversation.title}</span>
                  <span className="mt-2 block text-xs leading-6 text-[var(--ink-subtle)]">
                    {conversation.sceneType} · {conversation.status}
                  </span>
                </button>
              ))
            )}
          </div>
        </aside>

        <section className="medora-surface border border-[var(--line-soft)]">
          <header className="border-b border-[rgba(20,42,39,0.08)] px-5 py-5">
            <p className="text-xs font-semibold uppercase tracking-[0.24em] text-[var(--ink-subtle)]">
              Active Dialogue
            </p>
            <h2 className="mt-3 text-2xl font-semibold">
              {activeConversation?.title ?? "新的导诊会话"}
            </h2>
            <p className="mt-3 text-sm leading-7 text-[var(--ink-body)]">{status}</p>
            {error ? (
              <div className="mt-4 rounded-[1.2rem] border border-[rgba(190,92,67,0.18)] bg-[var(--danger-soft)] px-4 py-3 text-sm leading-6 text-[#8a4a3a]">
                {error}
              </div>
            ) : null}
          </header>

          <div className="min-h-[420px] space-y-5 overflow-y-auto px-5 py-6">
            {messages.length === 0 ? (
              <div className="grid gap-4">
                <div className="rounded-[1.5rem] border border-[rgba(20,42,39,0.08)] bg-[rgba(237,243,238,0.6)] px-5 py-6">
                  <p className="medora-display text-2xl font-semibold">从这里开始一次更像真实服务的医疗对话。</p>
                  <p className="mt-3 text-sm leading-7 text-[var(--ink-body)]">
                    你可以直接问挂什么科、是否还有号、怎么取消预约，或者继续追问就诊准备事项。
                  </p>
                </div>
                <div className="flex flex-wrap gap-3">
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
              messages.map((message) => (
                <article
                  key={message.messageId}
                  className={`flex ${message.role === "USER" ? "justify-end" : "justify-start"}`}
                >
                  <div
                    className={`max-w-[82%] rounded-[1.4rem] px-4 py-4 text-sm leading-7 ${
                      message.role === "USER"
                        ? "bg-[var(--ink-strong)] text-white shadow-[0_18px_36px_rgba(20,42,39,0.14)]"
                        : "border border-[rgba(20,42,39,0.08)] bg-[rgba(237,243,238,0.58)] text-[var(--ink-strong)]"
                    }`}
                  >
                    <div className="mb-2 text-xs font-semibold uppercase tracking-[0.22em] opacity-70">
                      {message.role === "USER" ? "You" : "Medora"}
                    </div>
                    {message.content || (message.pending ? "正在生成..." : "")}
                    {message.role === "ASSISTANT" && parseCitations(message.citations).length > 0 ? (
                      <div className="mt-4 space-y-3 border-t border-[rgba(20,42,39,0.1)] pt-4">
                        {parseCitations(message.citations).map((citation, index) => (
                          <div
                            className="rounded-[1.1rem] border border-[rgba(20,42,39,0.08)] bg-white/88 px-3 py-3 text-xs leading-6 text-[var(--ink-body)]"
                            key={`${message.messageId}-citation-${citation.sourceId ?? index}`}
                          >
                            <div className="font-semibold text-[var(--accent-main)]">
                              [{citation.index ?? index + 1}] {citation.title ?? "知识库引用"}
                            </div>
                            <div className="mt-1">{citation.snippet}</div>
                          </div>
                        ))}
                      </div>
                    ) : null}
                  </div>
                </article>
              ))
            )}
          </div>

          <form className="border-t border-[rgba(20,42,39,0.08)] p-4" onSubmit={sendMessage}>
            <div className="grid gap-3">
              <div className="flex flex-wrap gap-2">
                {quickPrompts.map((prompt) => (
                  <button
                    key={`chip-${prompt}`}
                    type="button"
                    onClick={() => setDraft(prompt)}
                    className="rounded-full border border-[var(--line-soft)] bg-white/82 px-3 py-2 text-xs font-semibold text-[var(--ink-body)] transition hover:border-[var(--accent-main)] hover:text-[var(--accent-main)]"
                  >
                    {prompt}
                  </button>
                ))}
              </div>
              <div className="grid gap-3 sm:grid-cols-[1fr_auto]">
                <textarea
                  className="min-h-28 resize-none rounded-[1.2rem] border border-[var(--line-soft)] bg-white/84 px-4 py-4 text-sm leading-7 text-[var(--ink-strong)] outline-none transition focus:border-[var(--accent-main)]"
                  disabled={isStreaming}
                  onChange={(event) => setDraft(event.target.value)}
                  value={draft}
                />
                <button
                  className="rounded-[1.2rem] bg-[var(--accent-main)] px-6 py-4 text-sm font-semibold text-white disabled:opacity-60"
                  disabled={isStreaming || !draft.trim()}
                  type="submit"
                >
                  {isStreaming ? "发送中" : "发送消息"}
                </button>
              </div>
            </div>
          </form>
        </section>

        <aside className="space-y-5">
          <div className="medora-surface border border-[var(--line-soft)] bg-[linear-gradient(180deg,rgba(237,243,238,0.92),rgba(255,255,255,0.9))] px-5 py-6">
            <p className="text-xs font-semibold uppercase tracking-[0.24em] text-[var(--ink-subtle)]">
              Assistant Scope
            </p>
            <h2 className="mt-4 text-xl font-semibold">Medora 当前能帮你做什么</h2>
            <ul className="mt-5 space-y-3">
              {assistantCapabilities.map((item) => (
                <li
                  key={item}
                  className="rounded-[1.2rem] border border-[rgba(20,42,39,0.08)] bg-white/76 px-4 py-4 text-sm leading-7 text-[var(--ink-body)]"
                >
                  {item}
                </li>
              ))}
            </ul>
          </div>

          <div className="medora-surface border border-[var(--line-soft)] px-5 py-6">
            <p className="text-xs font-semibold uppercase tracking-[0.24em] text-[var(--accent-main)]">
              Suggested Next Step
            </p>
            <div className="mt-5 rounded-[1.3rem] border border-[rgba(31,92,82,0.14)] bg-[rgba(31,92,82,0.06)] px-4 py-4 text-sm leading-7 text-[var(--ink-body)]">
              如果你已经确定就诊方向，可以直接切到预约中心查看实时号源；如果还不确定，继续追问症状和科室建议即可。
            </div>
            <div className="mt-5 flex flex-wrap gap-3">
              <Link
                href="/appointments"
                className="rounded-full bg-[var(--ink-strong)] px-4 py-3 text-sm font-semibold text-white"
              >
                去预约中心
              </Link>
              <Link
                href="/login"
                className="rounded-full border border-[var(--line-soft)] px-4 py-3 text-sm font-semibold text-[var(--ink-body)]"
              >
                查看账户状态
              </Link>
            </div>
          </div>
        </aside>
      </div>
    </PatientShell>
  );
}

function EmptyState({ text }: { text: string }) {
  return (
    <div className="rounded-[1.3rem] border border-dashed border-[var(--line-soft)] bg-[rgba(255,255,255,0.68)] px-4 py-8 text-sm leading-7 text-[var(--ink-subtle)]">
      {text}
    </div>
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
