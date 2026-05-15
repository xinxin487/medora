"use client";

import Link from "next/link";
import { FormEvent, useEffect, useMemo, useState } from "react";
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

export default function ChatPage() {
  const [conversations, setConversations] = useState<Conversation[]>([]);
  const [activeConversationId, setActiveConversationId] = useState("");
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [draft, setDraft] = useState("我最近咳嗽，应该挂哪个科？");
  const [status, setStatus] = useState("准备进行 Phase 6 Agent 联调。");
  const [error, setError] = useState<string | null>(null);
  const [isStreaming, setIsStreaming] = useState(false);

  const activeConversation = useMemo(
    () => conversations.find((item) => item.conversationId === activeConversationId) ?? null,
    [activeConversationId, conversations],
  );

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
        setActiveConversationId(result.list[0].conversationId);
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
      setActiveConversationId(conversation.conversationId);
      setMessages([]);
      setStatus("新会话已创建。");
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
    setActiveConversationId(conversation.conversationId);
    return conversation.conversationId;
  }

  async function loadMessages(conversationId: string) {
    await runAction(async () => {
      const result = await apiFetch<PageResponse<ChatMessage>>(
        `/chat/conversations/${conversationId}/messages?pageSize=100`,
      );
      setMessages(result.list);
      setStatus(`已读取 ${result.list.length} 条消息。`);
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
            setStatus(`助手消息生成中：${streamEvent.data.traceId}`);
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
                item.messageId === localAssistantId || item.pending && item.role === "ASSISTANT"
                  ? { ...item, content: `${item.content}${streamEvent.data.delta}` }
                  : item,
              ),
            );
          }

          if (streamEvent.event === "message_end") {
            setStatus("回复已完成并落库。");
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
    <main className="min-h-screen bg-[#fbfaf6] px-4 py-6 text-slate-950 sm:px-6">
      <section className="mx-auto grid max-w-7xl gap-5 lg:grid-cols-[320px_1fr]">
        <aside className="flex min-h-[calc(100vh-3rem)] flex-col border-r border-slate-200 bg-white px-4 py-5">
          <div className="flex items-center justify-between gap-3">
            <Link href="/" className="text-sm font-semibold text-emerald-700">
              Medora
            </Link>
            <button
              className="rounded-lg bg-slate-950 px-3 py-2 text-sm font-semibold text-white disabled:opacity-60"
              disabled={isStreaming}
              onClick={() => void createConversation()}
              type="button"
            >
              新会话
            </button>
          </div>

          <div className="mt-5 flex-1 space-y-2 overflow-y-auto">
            {conversations.length === 0 ? (
              <p className="rounded-lg bg-slate-50 px-3 py-4 text-sm text-slate-500">
                暂无会话。
              </p>
            ) : (
              conversations.map((conversation) => (
                <button
                  key={conversation.conversationId}
                  className={`w-full rounded-lg border px-3 py-3 text-left text-sm transition ${
                    conversation.conversationId === activeConversationId
                      ? "border-emerald-500 bg-emerald-50 text-emerald-950"
                      : "border-slate-200 bg-white text-slate-700 hover:border-slate-400"
                  }`}
                  disabled={isStreaming}
                  onClick={() => setActiveConversationId(conversation.conversationId)}
                  type="button"
                >
                  <span className="block font-semibold">{conversation.title}</span>
                  <span className="mt-1 block text-xs text-slate-500">
                    {conversation.status} · {conversation.sceneType}
                  </span>
                </button>
              ))
            )}
          </div>

          <div className="mt-4 rounded-lg border border-slate-200 bg-slate-50 px-3 py-3 text-sm leading-6 text-slate-600">
            {status}
          </div>
          {error ? (
            <div className="mt-3 rounded-lg border border-rose-200 bg-rose-50 px-3 py-3 text-sm leading-6 text-rose-700">
              {error}
            </div>
          ) : null}
        </aside>

        <section className="flex min-h-[calc(100vh-3rem)] flex-col bg-white">
          <header className="border-b border-slate-200 px-5 py-4">
            <p className="text-xs font-semibold uppercase tracking-[0.18em] text-slate-500">
              Phase 6 Agent
            </p>
            <h1 className="mt-2 text-2xl font-semibold">
              {activeConversation?.title ?? "导诊咨询"}
            </h1>
          </header>

          <div className="flex-1 space-y-4 overflow-y-auto px-5 py-6">
            {messages.length === 0 ? (
              <div className="flex h-full items-center justify-center text-sm text-slate-500">
                创建会话后即可发送消息。
              </div>
            ) : (
              messages.map((message) => (
                <article
                  key={message.messageId}
                  className={`flex ${message.role === "USER" ? "justify-end" : "justify-start"}`}
                >
                  <div
                    className={`max-w-[78%] rounded-lg px-4 py-3 text-sm leading-7 ${
                      message.role === "USER"
                        ? "bg-slate-950 text-white"
                        : "border border-slate-200 bg-slate-50 text-slate-800"
                    }`}
                  >
                    {message.content || (message.pending ? "正在生成..." : "")}
                    {message.role === "ASSISTANT" && parseCitations(message.citations).length > 0 ? (
                      <div className="mt-3 space-y-2 border-t border-slate-200 pt-3">
                        {parseCitations(message.citations).map((citation, index) => (
                          <div
                            className="rounded-md bg-white px-3 py-2 text-xs leading-5 text-slate-600"
                            key={`${message.messageId}-citation-${citation.sourceId ?? index}`}
                          >
                            <div className="font-semibold text-emerald-700">
                              [{citation.index ?? index + 1}] {citation.title ?? "知识库引用"}
                            </div>
                            <div>{citation.snippet}</div>
                          </div>
                        ))}
                      </div>
                    ) : null}
                  </div>
                </article>
              ))
            )}
          </div>

          <form className="border-t border-slate-200 p-4" onSubmit={sendMessage}>
            <div className="grid gap-3 sm:grid-cols-[1fr_auto]">
              <textarea
                className="min-h-24 resize-none rounded-lg border border-slate-200 px-4 py-3 text-sm leading-6 outline-none focus:border-emerald-500"
                disabled={isStreaming}
                onChange={(event) => setDraft(event.target.value)}
                value={draft}
              />
              <button
                className="rounded-lg bg-emerald-700 px-6 py-3 text-sm font-semibold text-white disabled:opacity-60"
                disabled={isStreaming || !draft.trim()}
                type="submit"
              >
                {isStreaming ? "发送中" : "发送"}
              </button>
            </div>
          </form>
        </section>
      </section>
    </main>
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
