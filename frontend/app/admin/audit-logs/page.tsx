"use client";

import { useEffect, useState } from "react";
import { apiFetch } from "@/lib/api/client";
import { useAuth } from "@/lib/hooks/use-auth";
import type { PageResponse } from "@/lib/types";
import { WorkspaceShell } from "@/components/workspace-shell";
import { Panel } from "@/components/panel";
import { Badge } from "@/components/badge";

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
  const { isLoading: authLoading } = useAuth("ADMIN");

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
      setError(cause instanceof Error ? cause.message : "加载审计日志失败。");
    } finally {
      setLoading(false);
    }
  }

  if (authLoading) return <LoadingSkeleton />;

  return (
    <WorkspaceShell
      role="admin"
      activeKey="audit"
      title="审计日志"
      subtitle="追踪关键写操作，包括后台知识库操作和预约动作。"
      aside={
        <Panel title="筛选条件" eyebrow="Filters">
          <form
            onSubmit={(e) => {
              e.preventDefault();
              setPageNo(1);
              void loadLogs(1);
            }}
            className="space-y-3"
          >
            <select
              className="w-full rounded-[1.1rem] border border-[var(--line-soft)] bg-white/80 px-3 py-2.5 text-sm outline-none transition focus:border-[var(--accent-main)]"
              value={operatorType}
              onChange={(e) => setOperatorType(e.target.value)}
            >
              <option value="">全部操作人</option>
              <option value="ADMIN">ADMIN</option>
              <option value="USER">USER</option>
              <option value="AGENT">AGENT</option>
              <option value="SYSTEM">SYSTEM</option>
            </select>
            <input
              className="w-full rounded-[1.1rem] border border-[var(--line-soft)] bg-white/80 px-3 py-2.5 text-sm outline-none transition focus:border-[var(--accent-main)]"
              placeholder="资源类型"
              value={resourceType}
              onChange={(e) => setResourceType(e.target.value)}
            />
            <input
              className="w-full rounded-[1.1rem] border border-[var(--line-soft)] bg-white/80 px-3 py-2.5 text-sm outline-none transition focus:border-[var(--accent-main)]"
              placeholder="请求 ID"
              value={requestId}
              onChange={(e) => setRequestId(e.target.value)}
            />
            <button
              className="w-full rounded-full border border-[var(--line-soft)] px-4 py-2.5 text-sm font-semibold text-[var(--ink-body)] disabled:opacity-60"
              disabled={loading}
              type="submit"
            >
              {loading ? "加载中..." : "刷新日志"}
            </button>
          </form>

          <div className="mt-4 flex flex-wrap gap-2">
            <Badge tone="soft">{page?.total ?? 0} 条</Badge>
            <Badge>第 {pageNo} 页</Badge>
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

      <Panel title="审计事件" eyebrow="Events">
        {!page?.list.length ? (
          <EmptyState text="暂无审计日志。" />
        ) : (
          <div className="space-y-3">
            {page.list.map((item) => (
              <div
                key={item.auditLogId}
                className="rounded-[1.4rem] border border-[var(--line-soft)] bg-white/84 p-4"
              >
                <div className="flex flex-wrap items-center justify-between gap-3">
                  <div className="flex flex-wrap items-center gap-2">
                    <p className="text-sm font-semibold text-[var(--ink-strong)]">
                      {item.actionType}
                    </p>
                    <Badge>{item.operatorType}</Badge>
                    <Badge tone="soft">{item.resourceType}</Badge>
                  </div>
                  <span className="text-xs text-[var(--ink-subtle)]">
                    {new Date(item.createdAt).toLocaleString("zh-CN", {
                      hour12: false,
                    })}
                  </span>
                </div>
                <p className="mt-2 text-sm text-[var(--ink-body)]">
                  {item.userLabel}
                  {item.resourceId && ` · ${item.resourceId}`}
                </p>
                <details className="mt-2">
                  <summary className="cursor-pointer text-xs font-semibold text-[var(--accent-main)]">
                    查看详情 JSON
                  </summary>
                  <pre className="mt-2 overflow-x-auto rounded-2xl bg-[#14362f] p-3 text-xs leading-6 text-slate-100">
                    {prettyJson(item.detailJson)}
                  </pre>
                </details>
              </div>
            ))}
          </div>
        )}
      </Panel>
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
          <div className="h-[calc(100vh-2.5rem)] animate-pulse rounded-[1.75rem] bg-slate-100" />
        </div>
      </div>
    </main>
  );
}
