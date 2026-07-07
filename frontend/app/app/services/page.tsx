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

function formatPeriod(code: string) {
  if (code === "AM") return "上午";
  if (code === "PM") return "下午";
  return code;
}

type TimelineItem = {
  id: string;
  date: string;
  title: string;
  detail: string;
  status: string;
  statusTone: "success" | "warning" | "default";
  type: "appointment" | "conversation";
};

function buildTimeline(
  appointments: Appointment[],
  conversations: Conversation[],
): TimelineItem[] {
  const items: TimelineItem[] = [];

  for (const a of appointments) {
    items.push({
      id: a.appointmentId,
      date: normalizeDate(a.bookedAt),
      title: `${a.departmentName} · ${a.doctorName}`,
      detail: `${a.scheduleDate} ${formatPeriod(a.periodCode)} · 预约号 ${a.appointmentNo}`,
      status: a.status === "CONFIRMED" ? "已确认" : a.status === "CANCELLED" ? "已取消" : a.status,
      statusTone: a.status === "CONFIRMED" ? "success" : a.status === "CANCELLED" ? "warning" : "default",
      type: "appointment",
    });
  }

  for (const c of conversations) {
    items.push({
      id: c.conversationId,
      date: normalizeDate(c.lastMessageAt),
      title: c.title ?? "导诊会话",
      detail: c.status === "ACTIVE" ? "进行中" : "已结束",
      status: c.status === "ACTIVE" ? "进行中" : "已结束",
      statusTone: c.status === "ACTIVE" ? "success" : "default",
      type: "conversation",
    });
  }

  items.sort((a, b) => {
    const left = b.date;
    const right = a.date;
    if (typeof left !== "string" || typeof right !== "string") {
      return String(left ?? "").localeCompare(String(right ?? ""));
    }
    return left.localeCompare(right);
  });
  return items;
}

function normalizeDate(value: unknown): string {
  if (value == null) return "";
  if (typeof value === "string") return value;
  if (Array.isArray(value)) {
    const [y, m, d, hh = 0, mm = 0, ss = 0] = value as number[];
    if (y == null) return "";
    const pad = (n: number) => String(n).padStart(2, "0");
    return `${y}-${pad(m)}-${pad(d)}T${pad(hh)}:${pad(mm)}:${pad(ss)}`;
  }
  return String(value);
}

export default function ServicesPage() {
  const { user, isLoading: authLoading } = useAuth("USER");
  const { error, runAction } = useAsyncAction();

  const [appointments, setAppointments] = useState<Appointment[]>([]);
  const [conversations, setConversations] = useState<Conversation[]>([]);

  useEffect(() => {
    if (!user) return;
    void runAction(async () => {
      const [apptRes, convRes] = await Promise.all([
        apiFetch<PageResponse<Appointment>>("/appointments?pageSize=20"),
        apiFetch<PageResponse<Conversation>>(
          "/chat/conversations?pageSize=20",
        ),
      ]);
      setAppointments(apptRes.list);
      setConversations(convRes.list);
    });
  }, [user]);

  if (authLoading) return <LoadingSkeleton />;

  const confirmedCount = appointments.filter(
    (a) => a.status === "CONFIRMED",
  ).length;
  const cancelledCount = appointments.filter(
    (a) => a.status === "CANCELLED",
  ).length;
  const timeline = buildTimeline(appointments, conversations);

  return (
    <WorkspaceShell
      role="patient"
      activeKey="services"
      title="我的服务"
      subtitle="近期预约、导诊和历史操作统一整理在这里。"
      aside={
        <>
          <Panel title="近期提醒" eyebrow="Notices">
            <div className="space-y-3 text-sm text-[var(--ink-body)]">
              <div className="rounded-2xl border border-[var(--line-soft)] bg-white/82 px-4 py-3">
                就诊前一天可再次确认号源与到院时间。
              </div>
              {confirmedCount > 0 && (
                <div className="rounded-2xl border border-[var(--line-soft)] bg-white/82 px-4 py-3">
                  你有 {confirmedCount} 个待就诊的预约。
                </div>
              )}
            </div>
          </Panel>

          <Panel title="服务偏好" eyebrow="Preferences">
            <div className="space-y-3 text-sm text-[var(--ink-body)]">
              <div className="rounded-2xl border border-[var(--line-soft)] bg-white/82 px-4 py-3">
                默认科室：{user?.defaultDepartment?.name ?? "尚未设置"}
              </div>
              <div className="rounded-2xl border border-[var(--line-soft)] bg-white/82 px-4 py-3">
                默认院区：{user?.defaultHospitalCode ?? "MAIN"}
              </div>
            </div>
            <div className="mt-3">
              <Link
                href="/app/profile"
                className="text-sm font-semibold text-[var(--accent-main)]"
              >
                修改偏好 →
              </Link>
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
          label="进行中预约"
          value={`${confirmedCount}`}
          hint="已确认且待就诊的预约数量。"
        />
        <MetricCard
          label="最近会话"
          value={`${conversations.length}`}
          hint="所有导诊对话记录。"
        />
        <MetricCard
          label="历史取消"
          value={`${cancelledCount}`}
          hint="已取消的预约记录。"
        />
      </div>

      <Panel title="服务记录" eyebrow="Timeline">
        {timeline.length === 0 ? (
          <EmptyState text="暂无服务记录。去预约中心或导诊助手开始使用。" />
        ) : (
          <div className="space-y-4">
            {timeline.map((item) => (
              <Link
                key={item.id}
                href={
                  item.type === "appointment"
                    ? "/app/appointments"
                    : "/app/assistant"
                }
                className="block rounded-[1.6rem] border border-[var(--line-soft)] bg-white/84 p-4 transition hover:border-[var(--accent-main)] hover:bg-white"
              >
                <div className="flex flex-wrap items-center justify-between gap-3">
                  <div className="flex items-center gap-3">
                    <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-[var(--accent-warm-soft)] text-xs font-semibold text-[var(--accent-main-strong)]">
                      {item.type === "appointment" ? "预" : "诊"}
                    </div>
                    <div>
                      <p className="text-sm font-semibold text-[var(--ink-strong)]">
                        {item.title}
                      </p>
                      <p className="mt-1 text-xs text-[var(--ink-body)]">
                        {item.detail}
                      </p>
                    </div>
                  </div>
                  <div className="flex items-center gap-2">
                    <Badge tone={item.statusTone}>{item.status}</Badge>
                    {item.date && (
                      <span className="text-xs text-[var(--ink-subtle)]">
                        {item.date.slice(0, 10)}
                      </span>
                    )}
                  </div>
                </div>
              </Link>
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
            <div className="grid gap-5 md:grid-cols-3">
              <div className="h-28 animate-pulse rounded-[1.6rem] bg-slate-100" />
              <div className="h-28 animate-pulse rounded-[1.6rem] bg-slate-100" />
              <div className="h-28 animate-pulse rounded-[1.6rem] bg-slate-100" />
            </div>
          </div>
          <div className="space-y-5">
            <div className="h-36 animate-pulse rounded-[1.75rem] bg-slate-100" />
            <div className="h-36 animate-pulse rounded-[1.75rem] bg-slate-100" />
          </div>
        </div>
      </div>
    </main>
  );
}
