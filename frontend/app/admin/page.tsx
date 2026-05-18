"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { AdminShell } from "@/components/admin-shell";
import { ApiClientError, apiFetch, getApiBaseUrl } from "@/lib/api/client";
import type { PageResponse } from "@/lib/types";

type OverviewStats = {
  knowledgeDocuments: number;
  conversations: number;
  traces: number;
  auditLogs: number;
};

const quickLinks = [
  {
    href: "/admin/knowledge",
    title: "知识库管理",
    description: "上传文档、查看索引状态、做检索回归。",
  },
  {
    href: "/admin/conversations",
    title: "会话查看",
    description: "按用户和会话维度排查摘要、记忆和消息链路。",
  },
  {
    href: "/admin/traces",
    title: "Agent Trace",
    description: "查看路由、工具、检索与耗时，定位失败环节。",
  },
  {
    href: "/admin/audit-logs",
    title: "审计日志",
    description: "追踪后台操作、预约动作与关键写操作。",
  },
];

export default function AdminOverviewPage() {
  const [stats, setStats] = useState<OverviewStats>({
    knowledgeDocuments: 0,
    conversations: 0,
    traces: 0,
    auditLogs: 0,
  });
  const [status, setStatus] = useState("正在加载后台概览。");
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    void loadOverview();
  }, []);

  async function loadOverview() {
    setError(null);
    setStatus("正在读取会话、Trace、审计和知识库总量。");
    try {
      const [documents, conversations, traces, auditLogs] = await Promise.all([
        apiFetch<PageResponse<unknown>>("/admin/knowledge/documents?pageSize=1"),
        apiFetch<PageResponse<unknown>>("/admin/conversations?pageSize=1"),
        apiFetch<PageResponse<unknown>>("/admin/agent-traces?pageSize=1"),
        apiFetch<PageResponse<unknown>>("/admin/audit-logs?pageSize=1"),
      ]);
      setStats({
        knowledgeDocuments: documents.total,
        conversations: conversations.total,
        traces: traces.total,
        auditLogs: auditLogs.total,
      });
      setStatus("后台概览已更新。");
    } catch (cause) {
      if (cause instanceof ApiClientError) {
        setError(`${cause.code}: ${cause.message}`);
      } else {
        setError("加载失败，请确认后端服务和管理员登录态可用。");
      }
    }
  }

  return (
    <AdminShell
      title="后台与可观测性总览"
      description="这一页聚合了 Phase 8 的最小可用后台入口，方便你从知识库、会话、Trace 和审计四个视角快速定位问题。"
      actions={
        <a
          className="rounded-full border border-emerald-200 bg-emerald-50 px-4 py-2 text-sm font-semibold text-emerald-700"
          href={`${getApiBaseUrl().replace(/\/api\/v1$/, "")}/actuator/prometheus`}
          target="_blank"
          rel="noreferrer"
        >
          打开 Prometheus 指标
        </a>
      }
    >
      <div className="grid gap-5 lg:grid-cols-[1.1fr_0.9fr]">
        <section className="space-y-5">
          <div className="grid gap-4 sm:grid-cols-2">
            <article className="rounded-[1.5rem] border border-slate-200 bg-white p-5">
              <p className="text-sm font-semibold text-slate-500">知识文档</p>
              <p className="mt-3 text-3xl font-semibold">{stats.knowledgeDocuments}</p>
            </article>
            <article className="rounded-[1.5rem] border border-slate-200 bg-white p-5">
              <p className="text-sm font-semibold text-slate-500">会话总数</p>
              <p className="mt-3 text-3xl font-semibold">{stats.conversations}</p>
            </article>
            <article className="rounded-[1.5rem] border border-slate-200 bg-white p-5">
              <p className="text-sm font-semibold text-slate-500">Trace 总数</p>
              <p className="mt-3 text-3xl font-semibold">{stats.traces}</p>
            </article>
            <article className="rounded-[1.5rem] border border-slate-200 bg-white p-5">
              <p className="text-sm font-semibold text-slate-500">审计事件</p>
              <p className="mt-3 text-3xl font-semibold">{stats.auditLogs}</p>
            </article>
          </div>

          <div className="rounded-[1.5rem] border border-slate-200 bg-white p-5">
            <div className="flex flex-wrap items-center justify-between gap-3">
              <div>
                <h2 className="text-lg font-semibold">后台入口</h2>
                <p className="mt-1 text-sm leading-6 text-slate-600">
                  这些入口都直接调用真实后台接口，适合做自测和联调排障。
                </p>
              </div>
            </div>

            <div className="mt-4 grid gap-4 sm:grid-cols-2">
              {quickLinks.map((item) => (
                <Link
                  key={item.href}
                  href={item.href}
                  className="rounded-[1.25rem] border border-slate-200 bg-slate-50 px-4 py-4 transition hover:border-slate-400 hover:bg-white"
                >
                  <h3 className="font-semibold">{item.title}</h3>
                  <p className="mt-2 text-sm leading-6 text-slate-600">{item.description}</p>
                </Link>
              ))}
            </div>
          </div>
        </section>

        <aside className="space-y-4">
          <div className="rounded-[1.5rem] border border-slate-200 bg-white p-5">
            <h2 className="text-lg font-semibold">运行状态</h2>
            <p className="mt-3 text-sm leading-7 text-slate-600">{status}</p>
            {error ? (
              <div className="mt-4 rounded-2xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm leading-6 text-rose-700">
                {error}
              </div>
            ) : null}
          </div>

          <div className="rounded-[1.5rem] border border-slate-200 bg-[#14362f] p-5 text-white">
            <h2 className="text-lg font-semibold">联调提醒</h2>
            <ul className="mt-4 space-y-3 text-sm leading-7 text-emerald-50">
              <li>先确认使用管理员账号登录，再访问各个后台页面。</li>
              <li>Prometheus 指标暴露在 `/actuator/prometheus`。</li>
              <li>会话页重点观察摘要、长期记忆和 token 预算 JSON。</li>
              <li>Trace 页重点观察路由、工具和检索结果的配合情况。</li>
            </ul>
          </div>
        </aside>
      </div>
    </AdminShell>
  );
}
