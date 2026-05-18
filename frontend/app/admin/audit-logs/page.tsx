"use client";

import { useEffect, useState } from "react";
import { AdminShell } from "@/components/admin-shell";
import { ApiClientError, apiFetch } from "@/lib/api/client";
import type { PageResponse } from "@/lib/types";

type AuditLogItem = {
  auditLogId: string;
  userId: string | null;
  userLabel: string;
  operatorType: string;
  actionType: string;
  resourceType: string;
  resourceId: string | null;
  requestId: string | null;
  detailJson: string;
  createdAt: string;
};

function prettyJson(value: string) {
  try {
    return JSON.stringify(JSON.parse(value), null, 2);
  } catch {
    return value;
  }
}

export default function AdminAuditLogsPage() {
  const [operatorType, setOperatorType] = useState("");
  const [resourceType, setResourceType] = useState("");
  const [requestId, setRequestId] = useState("");
  const [pageNo, setPageNo] = useState(1);
  const [page, setPage] = useState<PageResponse<AuditLogItem> | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    void loadLogs(pageNo);
  }, [pageNo]);

  async function loadLogs(targetPage: number) {
    setLoading(true);
    setError(null);
    try {
      const result = await apiFetch<PageResponse<AuditLogItem>>(
        `/admin/audit-logs?pageNo=${targetPage}&pageSize=20&operatorType=${encodeURIComponent(operatorType)}&resourceType=${encodeURIComponent(resourceType)}&requestId=${encodeURIComponent(requestId)}`,
      );
      setPage(result);
    } catch (cause) {
      if (cause instanceof ApiClientError) {
        setError(`${cause.code}: ${cause.message}`);
      } else {
        setError("加载审计日志失败。");
      }
    } finally {
      setLoading(false);
    }
  }

  return (
    <AdminShell
      title="审计日志"
      description="这里追踪关键写操作，包括后台知识库操作、预约动作和后续扩展出来的系统审计事件。"
    >
      <div className="space-y-5">
        <form
          className="rounded-[1.5rem] border border-slate-200 bg-white p-4"
          onSubmit={(event) => {
            event.preventDefault();
            setPageNo(1);
            void loadLogs(1);
          }}
        >
          <div className="grid gap-3 md:grid-cols-4">
            <select
              className="rounded-xl border border-slate-200 px-3 py-2 text-sm"
              value={operatorType}
              onChange={(event) => setOperatorType(event.target.value)}
            >
              <option value="">全部操作人</option>
              <option value="ADMIN">ADMIN</option>
              <option value="USER">USER</option>
              <option value="AGENT">AGENT</option>
              <option value="SYSTEM">SYSTEM</option>
            </select>
            <input
              className="rounded-xl border border-slate-200 px-3 py-2 text-sm"
              placeholder="resourceType"
              value={resourceType}
              onChange={(event) => setResourceType(event.target.value)}
            />
            <input
              className="rounded-xl border border-slate-200 px-3 py-2 text-sm"
              placeholder="requestId / 用户名 / 手机 / 邮箱"
              value={requestId}
              onChange={(event) => setRequestId(event.target.value)}
            />
            <button
              className="rounded-xl bg-slate-950 px-4 py-2 text-sm font-semibold text-white disabled:opacity-60"
              disabled={loading}
              type="submit"
            >
              {loading ? "加载中..." : "刷新日志"}
            </button>
          </div>
        </form>

        {error ? (
          <div className="rounded-[1.5rem] border border-rose-200 bg-rose-50 px-4 py-3 text-sm leading-6 text-rose-700">
            {error}
          </div>
        ) : null}

        <div className="rounded-[1.5rem] border border-slate-200 bg-white">
          <div className="border-b border-slate-200 px-4 py-3">
            <div className="flex items-center justify-between gap-3">
              <h2 className="text-base font-semibold">审计事件</h2>
              <span className="text-xs text-slate-500">{page?.total ?? 0} 条</span>
            </div>
          </div>
          <div className="divide-y divide-slate-100">
            {page?.list.length ? (
              page.list.map((item) => (
                <article className="px-4 py-4" key={item.auditLogId}>
                  <div className="flex flex-wrap items-center justify-between gap-3">
                    <div>
                      <div className="flex flex-wrap items-center gap-2">
                        <h3 className="font-semibold">{item.actionType}</h3>
                        <span className="rounded-full bg-slate-100 px-2 py-1 text-xs font-semibold text-slate-600">
                          {item.operatorType}
                        </span>
                        <span className="rounded-full bg-emerald-50 px-2 py-1 text-xs font-semibold text-emerald-700">
                          {item.resourceType}
                        </span>
                      </div>
                      <p className="mt-2 text-sm text-slate-600">
                        {item.userLabel} · {new Date(item.createdAt).toLocaleString("zh-CN", { hour12: false })}
                      </p>
                    </div>
                    <div className="text-right text-xs leading-6 text-slate-500">
                      <div>{item.resourceId ?? "无资源 ID"}</div>
                      <div>{item.requestId ?? "无 requestId"}</div>
                    </div>
                  </div>
                  <pre className="mt-3 overflow-x-auto rounded-2xl bg-slate-950 p-4 text-xs leading-6 text-slate-100">
                    {prettyJson(item.detailJson)}
                  </pre>
                </article>
              ))
            ) : (
              <div className="px-4 py-8 text-sm text-slate-500">暂无审计日志。</div>
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
      </div>
    </AdminShell>
  );
}
