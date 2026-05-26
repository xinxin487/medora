"use client";

import { FormEvent, useEffect, useState } from "react";
import { apiFetch } from "@/lib/api/client";
import { useAuth } from "@/lib/hooks/use-auth";
import { useAsyncAction } from "@/lib/hooks/use-async-action";
import type { PageResponse } from "@/lib/types";
import { WorkspaceShell } from "@/components/workspace-shell";
import { Panel } from "@/components/panel";
import { Badge } from "@/components/badge";

type KnowledgeDocument = {
  documentId: string;
  title: string;
  bizScope: string;
  publishStatus: string;
  ingestStatus: string;
  chunkCount: number;
  uploadedAt: string;
};

type KnowledgeSearchItem = {
  documentId: string;
  chunkId: number;
  chunkIndex: number;
  title: string;
  bizScope: string;
  score: number;
  sectionPath: string;
  snippet: string;
};

type KnowledgeSearchResponse = {
  query: string;
  items: KnowledgeSearchItem[];
};

export default function KnowledgeAdminPage() {
  const { isLoading: authLoading } = useAuth("ADMIN");
  const { isBusy, error, runAction } = useAsyncAction();

  const [documents, setDocuments] = useState<KnowledgeDocument[]>([]);
  const [title, setTitle] = useState("");
  const [bizScope, setBizScope] = useState("APPOINTMENT_RULE");
  const [file, setFile] = useState<File | null>(null);
  const [query, setQuery] = useState("");
  const [results, setResults] = useState<KnowledgeSearchItem[]>([]);
  const [message, setMessage] = useState("");

  useEffect(() => {
    void loadDocuments();
  }, []);

  async function loadDocuments() {
    await runAction(async () => {
      const result = await apiFetch<PageResponse<KnowledgeDocument>>(
        "/admin/knowledge/documents?pageSize=20",
      );
      setDocuments(result.list);
    }, false);
  }

  async function uploadDocument(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!file) return;
    const formData = new FormData();
    formData.set("file", file);
    formData.set("title", title);
    formData.set("bizScope", bizScope);
    await runAction(async () => {
      const uploaded = await apiFetch<KnowledgeDocument>(
        "/admin/knowledge/documents/upload",
        { method: "POST", body: formData },
      );
      setMessage(`已上传并切片：${uploaded.title}，${uploaded.chunkCount} 个切片。`);
      await loadDocuments();
    });
  }

  async function publishDocument(
    documentId: string,
    publishAction: "PUBLISH" | "OFFLINE",
  ) {
    await runAction(async () => {
      await apiFetch(`/admin/knowledge/documents/${documentId}/publish`, {
        method: "POST",
        body: JSON.stringify({ publishAction }),
      });
      setMessage(publishAction === "PUBLISH" ? "文档已发布。" : "文档已下线。");
      await loadDocuments();
    });
  }

  async function searchKnowledge(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    await runAction(async () => {
      const response = await apiFetch<KnowledgeSearchResponse>(
        "/admin/knowledge/search/test",
        { method: "POST", body: JSON.stringify({ query, bizScope, topK: 5 }) },
      );
      setResults(response.items);
      setMessage(`检索完成：${response.items.length} 条命中。`);
    });
  }

  if (authLoading) return <LoadingSkeleton />;

  return (
    <WorkspaceShell
      role="admin"
      activeKey="knowledge"
      title="知识库"
      subtitle="文档上传、索引管理与检索验证。"
      aside={
        <>
          <Panel title="上传文档" eyebrow="Upload">
            <form onSubmit={uploadDocument} className="space-y-3">
              <label className="block">
                <span className="text-sm font-semibold text-[var(--ink-body)]">文档标题</span>
                <input
                  className="mt-1.5 w-full rounded-[1.1rem] border border-[var(--line-soft)] bg-white/80 px-3 py-2.5 text-sm outline-none transition focus:border-[var(--accent-main)]"
                  value={title}
                  onChange={(e) => setTitle(e.target.value)}
                />
              </label>
              <label className="block">
                <span className="text-sm font-semibold text-[var(--ink-body)]">业务范围</span>
                <select
                  className="mt-1.5 w-full rounded-[1.1rem] border border-[var(--line-soft)] bg-white/80 px-3 py-2.5 text-sm outline-none transition focus:border-[var(--accent-main)]"
                  value={bizScope}
                  onChange={(e) => setBizScope(e.target.value)}
                >
                  <option value="APPOINTMENT_RULE">挂号规则</option>
                  <option value="GUIDE">导诊说明</option>
                  <option value="DEPARTMENT_INFO">科室信息</option>
                  <option value="DOCTOR_INFO">医生信息</option>
                  <option value="FAQ">FAQ</option>
                </select>
              </label>
              <input
                accept=".txt,.md,text/plain,text/markdown"
                className="w-full text-sm"
                onChange={(e) => setFile(e.target.files?.[0] ?? null)}
                type="file"
              />
              <button
                className="w-full rounded-full bg-[var(--accent-main)] px-4 py-2.5 text-sm font-semibold text-white disabled:opacity-60"
                disabled={isBusy || !file}
                type="submit"
              >
                上传并入库
              </button>
            </form>
          </Panel>

          <Panel title="检索验证" eyebrow="Retrieval">
            <form onSubmit={searchKnowledge} className="space-y-3">
              <textarea
                className="min-h-20 w-full resize-none rounded-[1.1rem] border border-[var(--line-soft)] bg-white/80 px-3 py-2.5 text-sm outline-none transition focus:border-[var(--accent-main)]"
                value={query}
                onChange={(e) => setQuery(e.target.value)}
              />
              <button
                className="w-full rounded-full border border-[var(--accent-main)] bg-[rgba(31,92,82,0.08)] px-4 py-2.5 text-sm font-semibold text-[var(--accent-main)] disabled:opacity-60"
                disabled={isBusy || !query.trim()}
                type="submit"
              >
                检索
              </button>
            </form>
          </Panel>
        </>
      }
    >
      {error ? (
        <div className="rounded-[1.2rem] border border-[rgba(190,92,67,0.18)] bg-[var(--danger-soft)] px-4 py-3 text-sm leading-6 text-[#8a4a3a]">
          {error}
        </div>
      ) : null}

      {message ? (
        <div className="rounded-[1.2rem] border border-[rgba(31,92,82,0.18)] bg-[var(--bg-soft)] px-4 py-3 text-sm leading-6 text-[var(--accent-main-strong)]">
          {message}
        </div>
      ) : null}

      <Panel title="文档列表" eyebrow="Documents">
        {documents.length === 0 ? (
          <EmptyState text="暂无文档，上传一个开始。" />
        ) : (
          <div className="space-y-3">
            {documents.map((doc) => (
              <div
                key={doc.documentId}
                className="rounded-[1.4rem] border border-[var(--line-soft)] bg-white/84 p-4"
              >
                <div className="flex flex-wrap items-center justify-between gap-3">
                  <div>
                    <p className="text-sm font-semibold text-[var(--ink-strong)]">
                      {doc.title}
                    </p>
                    <p className="mt-1 text-xs text-[var(--ink-body)]">
                      {doc.bizScope} · {doc.ingestStatus} · {doc.chunkCount} 个切片
                    </p>
                  </div>
                  <div className="flex items-center gap-2">
                    <Badge
                      tone={doc.publishStatus === "PUBLISHED" ? "success" : "default"}
                    >
                      {doc.publishStatus}
                    </Badge>
                    <button
                      className="rounded-full border border-[var(--accent-main)] bg-[rgba(31,92,82,0.08)] px-3 py-1.5 text-xs font-semibold text-[var(--accent-main)] disabled:opacity-60"
                      disabled={isBusy || doc.publishStatus === "PUBLISHED"}
                      onClick={() => void publishDocument(doc.documentId, "PUBLISH")}
                      type="button"
                    >
                      发布
                    </button>
                    <button
                      className="rounded-full border border-[var(--line-soft)] px-3 py-1.5 text-xs font-semibold text-[var(--ink-body)] disabled:opacity-60"
                      disabled={isBusy || doc.publishStatus !== "PUBLISHED"}
                      onClick={() => void publishDocument(doc.documentId, "OFFLINE")}
                      type="button"
                    >
                      下线
                    </button>
                  </div>
                </div>
              </div>
            ))}
          </div>
        )}
      </Panel>

      {results.length > 0 && (
        <Panel title="检索结果" eyebrow="Hits">
          <div className="space-y-3">
            {results.map((item) => (
              <div
                key={`${item.documentId}-${item.chunkId}`}
                className="rounded-[1.4rem] border border-[var(--line-soft)] bg-white/84 p-4"
              >
                <div className="flex items-center gap-2">
                  <p className="text-sm font-semibold text-[var(--ink-strong)]">
                    {item.title}
                  </p>
                  <Badge tone="soft">{item.score.toFixed(2)}</Badge>
                </div>
                <p className="mt-2 text-sm leading-6 text-[var(--ink-body)]">
                  {item.snippet}
                </p>
              </div>
            ))}
          </div>
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
            <div className="h-64 animate-pulse rounded-[1.75rem] bg-slate-100" />
          </div>
          <div className="space-y-5">
            <div className="h-64 animate-pulse rounded-[1.75rem] bg-slate-100" />
            <div className="h-48 animate-pulse rounded-[1.75rem] bg-slate-100" />
          </div>
        </div>
      </div>
    </main>
  );
}
