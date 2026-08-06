"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { apiFetch } from "@/lib/api/client";
import { useAuth } from "@/lib/hooks/use-auth";
import { useAsyncAction } from "@/lib/hooks/use-async-action";
import type { PageResponse } from "@/lib/types";
import type { Appointment, Conversation } from "@/lib/types/api";
import { WorkspaceShell } from "@/components/workspace-shell";
import { Panel } from "@/components/panel";
import { MetricCard } from "@/components/metric-card";
import { Badge } from "@/components/badge";

const nextSteps = [
  {
    title: "继续导诊",
    body: "接着上次关于咳嗽和挂号方向的会话继续提问。",
    href: "/app/assistant",
  },
  {
    title: "查看号源",
    body: "按本周时间与默认科室快速筛选可预约号源。",
    href: "/app/appointments",
  },
  {
    title: "回看服务",
    body: "查看最近预约、取消记录和就诊准备事项。",
    href: "/app/services",
  },
];

function getGreeting() {
  const h = new Date().getHours();
  if (h < 12) return "早上好";
  if (h < 18) return "下午好";
  return "晚上好";
}

function formatPeriod(code: string) {
  if (code === "AM") return "上午";
  if (code === "PM") return "下午";
  return code;
}

export default function DashboardPage() {
  const { user, isLoading: authLoading } = useAuth("USER");
  const { error, runAction } = useAsyncAction();

  const [appointments, setAppointments] = useState<Appointment[]>([]);
  const [conversations, setConversations] = useState<Conversation[]>([]);

  useEffect(() => {
    if (!user) return;
    void runAction(async () => {
      const [apptRes, convRes] = await Promise.all([
        apiFetch<PageResponse<Appointment>>(
          "/appointments?status=CONFIRMED&pageSize=5",
        ),
        apiFetch<PageResponse<Conversation>>(
          "/chat/conversations?pageSize=5",
        ),
      ]);
      setAppointments(apptRes.list);
      setConversations(convRes.list);
    });
  }, [user]);

  if (authLoading) {
    return <LoadingSkeleton />;
  }

  const confirmedCount = appointments.filter(
    (a) => a.status === "CONFIRMED",
  ).length;
  const activeConvs = conversations.filter(
    (c) => c.status === "ACTIVE",
  ).length;
  const nextAppt = appointments[0];

  return (
    <WorkspaceShell
      role="patient"
      activeKey="dashboard"
      title={`${getGreeting()}，${user?.displayName ?? "用户"}`}
      subtitle={
        confirmedCount > 0
          ? `你本周有 ${confirmedCount} 个已确认预约，${activeConvs > 0 ? `最近一次导诊仍可继续。` : "暂无进行中的导诊会话。"}`
          : "暂无待处理的预约和导诊。"
      }
      actions={
        <>
          <Link
            href="/app/assistant"
            className="rounded-full bg-[var(--accent-main)] px-5 py-3 text-sm font-semibold text-white transition hover:bg-[var(--accent-main-strong)]"
          >
            继续导诊
          </Link>
          <Link
            href="/app/appointments"
            className="rounded-full border border-[var(--line-soft)] bg-white px-5 py-3 text-sm font-semibold text-[var(--ink-strong)] transition hover:border-[var(--accent-main)] hover:text-[var(--accent-main)]"
          >
            查看号源
          </Link>
        </>
      }
      aside={
        <>
          <Panel title="本周预约" eyebrow="This Week">
            {nextAppt ? (
              <div className="rounded-[1.5rem] border border-[var(--line-soft)] bg-[var(--accent-warm-soft)] p-4">
                <p className="text-lg font-semibold text-[var(--ink-strong)]">
                  {nextAppt.scheduleDate} · {formatPeriod(nextAppt.periodCode)}
                </p>
                <p className="mt-2 text-sm text-[var(--ink-body)]">
                  {nextAppt.departmentName} · {nextAppt.doctorName}
                </p>
                <p className="mt-1 text-sm text-[var(--ink-subtle)]">
                  预约号 {nextAppt.appointmentNo}
                </p>
              </div>
            ) : (
              <EmptyState text="暂无已确认的预约。" />
            )}
          </Panel>

          <Panel title="常用偏好" eyebrow="Preferences">
            <div className="space-y-3 text-sm text-[var(--ink-body)]">
              <div className="rounded-2xl border border-[var(--line-soft)] bg-white/82 px-4 py-3">
                默认科室：{user?.defaultDepartment?.name ?? "尚未设置"}
              </div>
              <div className="rounded-2xl border border-[var(--line-soft)] bg-white/82 px-4 py-3">
                默认院区：{user?.defaultHospitalCode ?? "MAIN"}
              </div>
            </div>
          </Panel>

          <Panel title="最近提醒" eyebrow="Notices">
            <div className="space-y-3 text-sm text-[var(--ink-body)]">
              <div className="rounded-2xl border border-[var(--line-soft)] bg-white/82 px-4 py-3">
                就诊前一天可再次确认号源与到院时间。
              </div>
              {activeConvs > 0 && (
                <div className="rounded-2xl border border-[var(--line-soft)] bg-white/82 px-4 py-3">
                  上次导诊已保留会话上下文，可直接续聊。
                </div>
              )}
            </div>
          </Panel>
        </>
      }
    >
      {error ? (
        <div className="rounded-[1.2rem] border border-[rgba(190,92,67,0.18)] bg-[var(--danger-soft)] px-4 py-3 text-sm leading-6 text-[#8a4a3a]">
          {error}
        </div>
      ) : null}

      <div className="grid gap-5 md:grid-cols-3">
        <MetricCard
          label="进行中服务"
          value={`${confirmedCount + activeConvs}`}
          hint={`${confirmedCount} 个已确认预约，${activeConvs} 个可继续导诊主题。`}
        />
        <MetricCard
          label="最近预约"
          value={nextAppt ? nextAppt.departmentName : "无"}
          hint={
            nextAppt
              ? `${nextAppt.doctorName} · ${nextAppt.scheduleDate}`
              : "暂无预约记录"
          }
        />
        <MetricCard
          label="连续会话"
          value={`${conversations.length} 轮`}
          hint="最近一次问诊建议仍可直接续接。"
        />
      </div>

      <div className="grid gap-5 xl:grid-cols-[1.06fr_0.94fr]">
        <Panel title="下一步">
          <div className="space-y-3">
            {nextSteps.map((step) => (
              <Link
                key={step.title}
                href={step.href}
                className="block rounded-[1.5rem] border border-[var(--line-soft)] bg-white/84 p-4 transition hover:border-[var(--accent-main)] hover:bg-white"
              >
                <div className="flex items-center justify-between gap-3">
                  <h3 className="text-lg font-semibold text-[var(--ink-strong)]">
                    {step.title}
                  </h3>
                  <span className="text-sm font-semibold text-[var(--accent-main)]">
                    进入
                  </span>
                </div>
                <p className="mt-2 text-sm leading-6 text-[var(--ink-body)]">
                  {step.body}
                </p>
              </Link>
            ))}
          </div>
        </Panel>

        <Panel title="近期服务">
          <div className="space-y-4">
            {conversations.length > 0 ? (
              <div className="rounded-[1.6rem] border border-[var(--line-soft)] bg-white/84 p-4">
                <div className="flex items-center justify-between gap-3">
                  <p className="text-sm font-semibold text-[var(--ink-strong)]">
                    导诊助手
                  </p>
                  <Badge tone="soft">可继续</Badge>
                </div>
                <p className="mt-3 text-sm leading-7 text-[var(--ink-body)]">
                  {conversations[0].title ?? "最近一次导诊对话"}
                </p>
              </div>
            ) : (
              <div className="rounded-[1.6rem] border border-[var(--line-soft)] bg-white/84 p-4">
                <div className="flex items-center justify-between gap-3">
                  <p className="text-sm font-semibold text-[var(--ink-strong)]">
                    导诊助手
                  </p>
                  <Badge>未开始</Badge>
                </div>
                <p className="mt-3 text-sm leading-7 text-[var(--ink-body)]">
                  还没有导诊记录，去开始一次导诊对话吧。
                </p>
              </div>
            )}

            {nextAppt ? (
              <div className="rounded-[1.6rem] border border-[var(--line-soft)] bg-white/84 p-4">
                <div className="flex items-center justify-between gap-3">
                  <p className="text-sm font-semibold text-[var(--ink-strong)]">
                    预约服务
                  </p>
                  <Badge tone="success">已确认</Badge>
                </div>
                <p className="mt-3 text-sm leading-7 text-[var(--ink-body)]">
                  {nextAppt.scheduleDate}
                  {formatPeriod(nextAppt.periodCode)} ·{" "}
                  {nextAppt.departmentName} · {nextAppt.doctorName}
                </p>
              </div>
            ) : (
              <div className="rounded-[1.6rem] border border-[var(--line-soft)] bg-white/84 p-4">
                <div className="flex items-center justify-between gap-3">
                  <p className="text-sm font-semibold text-[var(--ink-strong)]">
                    预约服务
                  </p>
                  <Badge>暂无</Badge>
                </div>
                <p className="mt-3 text-sm leading-7 text-[var(--ink-body)]">
                  尚无预约记录，去预约中心查看号源。
                </p>
              </div>
            )}
          </div>
        </Panel>
      </div>
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
            <div className="grid gap-5 md:grid-cols-3">
              <div className="h-28 animate-pulse rounded-[1.6rem] bg-slate-100" />
              <div className="h-28 animate-pulse rounded-[1.6rem] bg-slate-100" />
              <div className="h-28 animate-pulse rounded-[1.6rem] bg-slate-100" />
            </div>
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
