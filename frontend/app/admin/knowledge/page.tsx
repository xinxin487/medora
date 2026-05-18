"use client";

import Link from "next/link";
import { FormEvent, useEffect, useState } from "react";
import { AdminShell } from "@/components/admin-shell";
import { ApiClientError, apiFetch } from "@/lib/api/client";
import type { PageResponse } from "@/lib/types";

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
  const [documents, setDocuments] = useState<KnowledgeDocument[]>([]);
  const [title, setTitle] = useState("挂号规则说明");
  const [bizScope, setBizScope] = useState("APPOINTMENT_RULE");
  const [file, setFile] = useState<File | null>(null);
  const [query, setQuery] = useState("取消挂号");
  const [results, setResults] = useState<KnowledgeSearchItem[]>([]);
  const [status, setStatus] = useState("准备上传知识文档。");
  const [error, setError] = useState<string | null>(null);
  const [isBusy, setIsBusy] = useState(false);

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
    if (!file) {
      setError("请选择 .txt 或 .md 文档。");
      return;
    }

    const formData = new FormData();
    formData.set("file", file);
    formData.set("title", title);
    formData.set("bizScope", bizScope);

    await runAction(async () => {
      const uploaded = await apiFetch<KnowledgeDocument>("/admin/knowledge/documents/upload", {
        method: "POST",
        body: formData,
      });
      setStatus(`已上传并切片：${uploaded.title}，${uploaded.chunkCount} 个切片。`);
      await loadDocuments();
    });
  }

  async function publishDocument(documentId: string, publishAction: "PUBLISH" | "OFFLINE") {
    await runAction(async () => {
      await apiFetch(`/admin/knowledge/documents/${documentId}/publish`, {
        method: "POST",
        body: JSON.stringify({ publishAction }),
      });
      setStatus(publishAction === "PUBLISH" ? "文档已发布。" : "文档已下线。");
      await loadDocuments();
    });
  }

  async function searchKnowledge(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    await runAction(async () => {
      const response = await apiFetch<KnowledgeSearchResponse>("/admin/knowledge/search/test", {
        method: "POST",
        body: JSON.stringify({ query, bizScope, topK: 5 }),
      });
      setResults(response.items);
      setStatus(`检索完成：${response.items.length} 条命中。`);
    });
  }

  async function runAction(action: () => Promise<void>, resetError = true) {
    if (resetError) {
      setError(null);
    }
    setIsBusy(true);
    try {
      await action();
    } catch (cause) {
      if (cause instanceof ApiClientError) {
        setError(`${cause.code}: ${cause.message}`);
      } else {
        setError("请求失败，请确认已使用管理员账号登录。");
      }
    } finally {
      setIsBusy(false);
    }
  }

  return (
    <AdminShell
      title="知识库管理"
      description="这里继续承担知识文档上传、发布和检索测试，同时也成为后台导航的一部分，方便和会话、Trace、审计联动排查。"
      actions={
        <Link className="rounded-full bg-slate-950 px-4 py-2 text-sm font-semibold text-white" href="/chat">
          打开对话联调
        </Link>
      }
    >
      <div className="grid gap-5 lg:grid-cols-[360px_1fr]">
          <aside className="space-y-4">
            <form className="space-y-3 border border-slate-200 bg-white p-4" onSubmit={uploadDocument}>
              <h2 className="text-base font-semibold">上传文档</h2>
              <input
                className="w-full rounded-md border border-slate-200 px-3 py-2 text-sm"
                onChange={(event) => setTitle(event.target.value)}
                value={title}
              />
              <select
                className="w-full rounded-md border border-slate-200 px-3 py-2 text-sm"
                onChange={(event) => setBizScope(event.target.value)}
                value={bizScope}
              >
                <option value="APPOINTMENT_RULE">挂号规则</option>
                <option value="GUIDE">导诊说明</option>
                <option value="DEPARTMENT_INFO">科室信息</option>
                <option value="DOCTOR_INFO">医生信息</option>
                <option value="FAQ">FAQ</option>
              </select>
              <input
                accept=".txt,.md,text/plain,text/markdown"
                className="w-full text-sm"
                onChange={(event) => setFile(event.target.files?.[0] ?? null)}
                type="file"
              />
              <button
                className="w-full rounded-md bg-slate-950 px-4 py-2 text-sm font-semibold text-white disabled:opacity-60"
                disabled={isBusy}
                type="submit"
              >
                上传并入库
              </button>
            </form>

            <form className="space-y-3 border border-slate-200 bg-white p-4" onSubmit={searchKnowledge}>
              <h2 className="text-base font-semibold">检索测试</h2>
              <textarea
                className="min-h-24 w-full resize-none rounded-md border border-slate-200 px-3 py-2 text-sm"
                onChange={(event) => setQuery(event.target.value)}
                value={query}
              />
              <button
                className="w-full rounded-md bg-emerald-700 px-4 py-2 text-sm font-semibold text-white disabled:opacity-60"
                disabled={isBusy || !query.trim()}
                type="submit"
              >
                检索
              </button>
            </form>

            <div className="border border-slate-200 bg-white px-4 py-3 text-sm leading-6 text-slate-600">
              {status}
            </div>
            {error ? (
              <div className="border border-rose-200 bg-rose-50 px-4 py-3 text-sm leading-6 text-rose-700">
                {error}
              </div>
            ) : null}
          </aside>

          <section className="space-y-5">
            <div className="border border-slate-200 bg-white">
              <div className="border-b border-slate-200 px-4 py-3">
                <h2 className="text-base font-semibold">文档列表</h2>
              </div>
              <div className="divide-y divide-slate-100">
                {documents.length === 0 ? (
                  <div className="px-4 py-8 text-sm text-slate-500">暂无文档。</div>
                ) : (
                  documents.map((document) => (
                    <article
                      className="grid gap-3 px-4 py-4 sm:grid-cols-[1fr_auto]"
                      key={document.documentId}
                    >
                      <div>
                        <h3 className="font-semibold">{document.title}</h3>
                        <p className="mt-1 text-sm text-slate-500">
                          {document.bizScope} · {document.ingestStatus} · {document.chunkCount} 个切片
                        </p>
                        <p className="mt-1 text-xs text-slate-400">{document.documentId}</p>
                      </div>
                      <div className="flex items-center gap-2">
                        <span className="rounded-md bg-slate-100 px-2 py-1 text-xs font-semibold text-slate-600">
                          {document.publishStatus}
                        </span>
                        <button
                          className="rounded-md border border-emerald-200 px-3 py-2 text-sm font-semibold text-emerald-700 disabled:opacity-60"
                          disabled={isBusy || document.publishStatus === "PUBLISHED"}
                          onClick={() => void publishDocument(document.documentId, "PUBLISH")}
                          type="button"
                        >
                          发布
                        </button>
                        <button
                          className="rounded-md border border-slate-200 px-3 py-2 text-sm font-semibold text-slate-700 disabled:opacity-60"
                          disabled={isBusy || document.publishStatus !== "PUBLISHED"}
                          onClick={() => void publishDocument(document.documentId, "OFFLINE")}
                          type="button"
                        >
                          下线
                        </button>
                      </div>
                    </article>
                  ))
                )}
              </div>
            </div>

            <div className="border border-slate-200 bg-white">
              <div className="border-b border-slate-200 px-4 py-3">
                <h2 className="text-base font-semibold">检索结果</h2>
              </div>
              <div className="divide-y divide-slate-100">
                {results.length === 0 ? (
                  <div className="px-4 py-8 text-sm text-slate-500">暂无检索结果。</div>
                ) : (
                  results.map((item) => (
                    <article className="px-4 py-4" key={`${item.documentId}-${item.chunkId}`}>
                      <div className="flex flex-wrap items-center gap-2">
                        <h3 className="font-semibold">{item.title}</h3>
                        <span className="rounded-md bg-emerald-50 px-2 py-1 text-xs font-semibold text-emerald-700">
                          {item.score.toFixed(2)}
                        </span>
                      </div>
                      <p className="mt-2 text-sm leading-6 text-slate-700">{item.snippet}</p>
                    </article>
                  ))
                )}
              </div>
            </div>
          </section>
        </div>
    </AdminShell>
  );
}
