"use client";

import { useEffect, useState } from "react";
import type { FormEvent } from "react";
import Link from "next/link";
import { apiFetch } from "@/lib/api/client";
import { useAuth } from "@/lib/hooks/use-auth";
import { useAsyncAction } from "@/lib/hooks/use-async-action";
import type { PageResponse } from "@/lib/types";
import type {
  Department,
  Doctor,
  ScheduleSlot,
  Appointment,
  AppointmentCreated,
} from "@/lib/types/api";
import { WorkspaceShell } from "@/components/workspace-shell";
import { Panel } from "@/components/panel";
import { Badge } from "@/components/badge";

const tomorrow = new Date(Date.now() + 24 * 60 * 60 * 1000)
  .toISOString()
  .slice(0, 10);

function formatPeriod(value: string) {
  if (value === "AM") return "上午";
  if (value === "PM") return "下午";
  return value;
}

export default function AppointmentsPage() {
  const { user, isLoading: authLoading } = useAuth("USER");
  const { isBusy, error, runAction } = useAsyncAction();

  const [scheduleDate, setScheduleDate] = useState(tomorrow);
  const [periodCode, setPeriodCode] = useState("");
  const [departments, setDepartments] = useState<Department[]>([]);
  const [doctors, setDoctors] = useState<Doctor[]>([]);
  const [slots, setSlots] = useState<ScheduleSlot[]>([]);
  const [appointments, setAppointments] = useState<Appointment[]>([]);
  const [selectedDepartmentId, setSelectedDepartmentId] = useState("");
  const [selectedDoctorId, setSelectedDoctorId] = useState("");
  const [selectedSlotId, setSelectedSlotId] = useState("");
  const [message, setMessage] = useState("");
  const [deptInitialized, setDeptInitialized] = useState(false);

  const selectedDepartment =
    departments.find((d) => d.departmentId === selectedDepartmentId) ?? null;
  const selectedDoctor =
    doctors.find((d) => d.doctorId === selectedDoctorId) ?? null;
  const selectedSlot =
    slots.find((s) => s.slotId === selectedSlotId) ?? null;

  useEffect(() => {
    void loadDepartments();
    void loadAppointments();
  }, []);

  // Auto-select default department from user preferences
  useEffect(() => {
    if (deptInitialized || !user?.defaultDepartment || departments.length === 0) return;
    const defaultId = user.defaultDepartment.departmentId;
    if (departments.some((d) => d.departmentId === defaultId)) {
      setSelectedDepartmentId(defaultId);
    }
    setDeptInitialized(true);
  }, [user, departments, deptInitialized]);

  useEffect(() => {
    void loadDoctors(selectedDepartmentId);
  }, [selectedDepartmentId]);

  useEffect(() => {
    void loadSlots();
  }, [scheduleDate, selectedDepartmentId, selectedDoctorId, periodCode]);

  async function loadDepartments() {
    await runAction(async () => {
      const result = await apiFetch<PageResponse<Department>>(
        "/departments?status=ACTIVE&pageSize=24",
      );
      setDepartments(result.list);
      if (!selectedDepartmentId && result.list.length > 0) {
        setSelectedDepartmentId(result.list[0].departmentId);
      }
    }, false);
  }

  async function loadDoctors(departmentId: string) {
    await runAction(async () => {
      const search = new URLSearchParams({
        status: "ACTIVE",
        pageSize: "50",
      });
      if (departmentId) search.set("departmentId", departmentId);
      const result = await apiFetch<PageResponse<Doctor>>(
        `/doctors?${search.toString()}`,
      );
      setDoctors(result.list);
      setSelectedDoctorId((cur) =>
        cur && result.list.some((d) => d.doctorId === cur)
          ? cur
          : result.list[0]?.doctorId ?? "",
      );
    }, false);
  }

  async function loadSlots(event?: FormEvent<HTMLFormElement>) {
    event?.preventDefault();
    await runAction(async () => {
      const search = new URLSearchParams({
        scheduleDate,
        status: "OPEN",
        pageSize: "50",
      });
      if (selectedDepartmentId) search.set("departmentId", selectedDepartmentId);
      if (selectedDoctorId) search.set("doctorId", selectedDoctorId);
      if (periodCode) search.set("periodCode", periodCode);
      const result = await apiFetch<PageResponse<ScheduleSlot>>(
        `/schedule-slots?${search.toString()}`,
      );
      setSlots(result.list);
      setSelectedSlotId((cur) =>
        cur && result.list.some((s) => s.slotId === cur)
          ? cur
          : result.list[0]?.slotId ?? "",
      );
      setMessage(`已更新 ${result.list.length} 个可预约号源。`);
    }, false);
  }

  async function loadAppointments() {
    await runAction(async () => {
      const result = await apiFetch<PageResponse<Appointment>>(
        "/appointments?pageSize=20",
      );
      setAppointments(result.list);
    }, false);
  }

  async function bookAppointment() {
    if (!selectedSlotId) return;
    await runAction(async () => {
      const result = await apiFetch<AppointmentCreated>("/appointments", {
        method: "POST",
        headers: { "Idempotency-Key": crypto.randomUUID() },
        body: JSON.stringify({
          slotId: selectedSlotId,
          remark: "预约中心创建",
        }),
      });
      setMessage(`预约成功：${result.appointmentNo}`);
      await loadSlots();
      await loadAppointments();
    });
  }

  async function cancelAppointment(appointmentId: string) {
    await runAction(async () => {
      await apiFetch(`/appointments/${appointmentId}/cancel`, {
        method: "POST",
        body: JSON.stringify({ cancelReason: "用户在预约中心取消" }),
      });
      setMessage("预约已取消，号源已回补。");
      await loadSlots();
      await loadAppointments();
    });
  }

  if (authLoading) return <LoadingSkeleton />;

  return (
    <WorkspaceShell
      role="patient"
      activeKey="appointments"
      title="预约服务"
      subtitle="从科室筛选到锁定号源，在同一页面完成选择与确认。"
      actions={
        <Link
          href="/app/assistant"
          className="rounded-full border border-[var(--accent-main)] bg-[rgba(31,92,82,0.08)] px-5 py-3 text-sm font-semibold text-[var(--accent-main)]"
        >
          先问导诊助手
        </Link>
      }
      aside={
        <>
          <Panel title="当前选择" eyebrow="Selection">
            <div className="space-y-3">
              <SummaryCard label="科室" value={selectedDepartment?.name ?? "未限定"} />
              <SummaryCard
                label="医生"
                value={
                  selectedDoctor
                    ? `${selectedDoctor.name}${selectedDoctor.title ? ` · ${selectedDoctor.title}` : ""}`
                    : "未限定"
                }
              />
              <SummaryCard label="日期" value={scheduleDate} />
              <SummaryCard label="时段" value={periodCode ? formatPeriod(periodCode) : "全天"} />
            </div>
            <div className="mt-4 rounded-[1.4rem] border border-[rgba(31,92,82,0.14)] bg-white/76 p-4">
              <p className="text-sm font-semibold text-[var(--accent-main)]">选中的号源</p>
              <p className="mt-2 text-sm leading-7 text-[var(--ink-body)]">
                {selectedSlot
                  ? `${selectedSlot.doctor.name} · ${selectedSlot.scheduleDate} · ${formatPeriod(selectedSlot.periodCode)}`
                  : "尚未选择号源。"}
              </p>
            </div>
            <button
              className="mt-4 w-full rounded-full bg-[var(--accent-main)] px-5 py-3 text-sm font-semibold text-white transition hover:bg-[var(--accent-main-strong)] disabled:opacity-60"
              disabled={isBusy || !selectedSlotId}
              onClick={() => void bookAppointment()}
              type="button"
            >
              预约当前选择
            </button>
          </Panel>

          <Panel
            title="我的预约"
            eyebrow="My Appointments"
            actions={
              <button
                className="rounded-full border border-[var(--line-soft)] px-3 py-1.5 text-xs font-semibold text-[var(--ink-body)]"
                disabled={isBusy}
                onClick={() => void loadAppointments()}
                type="button"
              >
                刷新
              </button>
            }
          >
            {appointments.length === 0 ? (
              <EmptyState text="当前账户还没有预约记录。" />
            ) : (
              <div className="space-y-3">
                {appointments.map((appt) => (
                  <article
                    key={appt.appointmentId}
                    className="rounded-[1.4rem] border border-[rgba(20,42,39,0.08)] bg-white/78 p-4"
                  >
                    <div className="flex flex-wrap items-start justify-between gap-2">
                      <div>
                        <p className="text-sm font-semibold text-[var(--ink-strong)]">
                          {appt.departmentName} · {appt.doctorName}
                        </p>
                        <p className="mt-1 text-sm text-[var(--ink-body)]">
                          {appt.scheduleDate} · {formatPeriod(appt.periodCode)}
                        </p>
                        <p className="mt-1 text-xs text-[var(--ink-subtle)]">
                          {appt.appointmentNo}
                        </p>
                      </div>
                      <Badge
                        tone={appt.status === "CONFIRMED" ? "success" : "default"}
                      >
                        {appt.status}
                      </Badge>
                    </div>
                    <div className="mt-3 flex items-center justify-between gap-2">
                      <span className="text-sm font-semibold text-[var(--accent-warm)]">
                        ¥{appt.fee}
                      </span>
                      {appt.status === "CONFIRMED" && (
                        <button
                          className="rounded-full border border-[rgba(190,92,67,0.22)] bg-[var(--danger-soft)] px-3 py-1.5 text-xs font-semibold text-[#8a4a3a] disabled:opacity-60"
                          disabled={isBusy}
                          onClick={() => void cancelAppointment(appt.appointmentId)}
                          type="button"
                        >
                          取消
                        </button>
                      )}
                    </div>
                  </article>
                ))}
              </div>
            )}
          </Panel>
        </>
      }
    >
      {error ? (
        <div className="rounded-[1.2rem] border border-[rgba(190,92,67,0.18)] bg-[var(--danger-soft)] px-4 py-3 text-sm leading-6 text-[#8a4a3a]">
          {error}
        </div>
      ) : null}

      <Panel
        title="筛选条件"
        eyebrow="Filters"
        actions={
          <>
            {user?.defaultDepartment && (
              <Badge tone="soft">
                默认科室：{user.defaultDepartment.name}
              </Badge>
            )}
            <Badge>{message}</Badge>
          </>
        }
      >
        <form onSubmit={loadSlots}>
          <div className="grid gap-4 md:grid-cols-3">
            <label className="block">
              <span className="text-sm font-semibold text-[var(--ink-body)]">就诊日期</span>
              <input
                className="mt-2 w-full rounded-[1.1rem] border border-[var(--line-soft)] bg-white/80 px-4 py-3 text-sm outline-none transition focus:border-[var(--accent-main)]"
                type="date"
                value={scheduleDate}
                onChange={(e) => setScheduleDate(e.target.value)}
              />
            </label>
            <label className="block">
              <span className="text-sm font-semibold text-[var(--ink-body)]">时段</span>
              <select
                className="mt-2 w-full rounded-[1.1rem] border border-[var(--line-soft)] bg-white/80 px-4 py-3 text-sm outline-none transition focus:border-[var(--accent-main)]"
                value={periodCode}
                onChange={(e) => setPeriodCode(e.target.value)}
              >
                <option value="">全天</option>
                <option value="AM">上午</option>
                <option value="PM">下午</option>
              </select>
            </label>
            <div className="flex items-end">
              <button
                className="w-full rounded-full border border-[var(--line-soft)] px-4 py-3 text-sm font-semibold text-[var(--ink-body)] transition hover:border-[var(--accent-main)] disabled:opacity-60"
                disabled={isBusy}
                type="submit"
              >
                更新号源 ({slots.length})
              </button>
            </div>
          </div>
        </form>
      </Panel>

      <div className="grid gap-5 xl:grid-cols-2">
        <Panel
          title="科室"
          eyebrow="Departments"
          actions={
            <button
              className="rounded-full border border-[var(--line-soft)] px-3 py-1.5 text-xs font-semibold text-[var(--ink-body)]"
              onClick={() => setSelectedDepartmentId("")}
              type="button"
            >
              查看全部
            </button>
          }
        >
          <div className="grid gap-3 sm:grid-cols-2">
            {departments.map((dept) => {
              const active = dept.departmentId === selectedDepartmentId;
              return (
                <button
                  key={dept.departmentId}
                  type="button"
                  onClick={() => setSelectedDepartmentId(dept.departmentId)}
                  className={`rounded-[1.4rem] border px-4 py-4 text-left transition ${
                    active
                      ? "border-[var(--accent-main)] bg-[rgba(31,92,82,0.08)]"
                      : "border-[rgba(20,42,39,0.08)] bg-white/78 hover:border-[var(--accent-warm)]"
                  }`}
                >
                  <p className="text-sm font-semibold text-[var(--ink-strong)]">{dept.name}</p>
                  <p className="mt-1 text-xs leading-5 text-[var(--ink-body)]">
                    {dept.description || dept.category || "门诊服务"}
                  </p>
                </button>
              );
            })}
          </div>
        </Panel>

        <Panel
          title="医生"
          eyebrow="Doctors"
          actions={
            <button
              className="rounded-full border border-[var(--line-soft)] px-3 py-1.5 text-xs font-semibold text-[var(--ink-body)]"
              onClick={() => setSelectedDoctorId("")}
              type="button"
            >
              查看全部
            </button>
          }
        >
          {doctors.length === 0 ? (
            <EmptyState text="当前条件下暂无医生信息。" />
          ) : (
            <div className="space-y-3">
              {doctors.map((doc) => {
                const active = doc.doctorId === selectedDoctorId;
                return (
                  <button
                    key={doc.doctorId}
                    type="button"
                    onClick={() => setSelectedDoctorId(doc.doctorId)}
                    className={`flex w-full items-center justify-between gap-3 rounded-[1.4rem] border px-4 py-3 text-left transition ${
                      active
                        ? "border-[var(--accent-main)] bg-[rgba(31,92,82,0.08)]"
                        : "border-[rgba(20,42,39,0.08)] bg-white/78 hover:border-[var(--accent-warm)]"
                    }`}
                  >
                    <div>
                      <p className="text-sm font-semibold text-[var(--ink-strong)]">
                        {doc.name}{doc.title ? ` · ${doc.title}` : ""}
                      </p>
                      <p className="mt-1 text-xs text-[var(--ink-body)]">
                        {doc.specialty || "门诊接诊"}
                      </p>
                    </div>
                    <Badge tone="soft">{doc.department.name}</Badge>
                  </button>
                );
              })}
            </div>
          )}
        </Panel>
      </div>

      <Panel title="可预约号源" eyebrow="Slots">
        {slots.length === 0 ? (
          <EmptyState text="当前筛选条件下暂无开放号源，请调整日期、科室或时段。" />
        ) : (
          <div className="space-y-3">
            {slots.map((slot) => {
              const active = slot.slotId === selectedSlotId;
              return (
                <button
                  key={slot.slotId}
                  type="button"
                  onClick={() => setSelectedSlotId(slot.slotId)}
                  className={`flex w-full items-center justify-between gap-4 rounded-[1.5rem] border px-4 py-4 text-left transition ${
                    active
                      ? "border-[var(--accent-main)] bg-[rgba(31,92,82,0.08)]"
                      : "border-[rgba(20,42,39,0.08)] bg-white/80 hover:border-[var(--accent-warm)]"
                  }`}
                >
                  <div>
                    <p className="text-sm font-semibold text-[var(--ink-strong)]">
                      {slot.department.name} · {slot.doctor.name}
                    </p>
                    <p className="mt-1 text-xs text-[var(--ink-body)]">
                      {slot.scheduleDate} · {formatPeriod(slot.periodCode)} ·{" "}
                      {slot.startTime ?? "--"}-{slot.endTime ?? "--"}
                    </p>
                    <p className="mt-1 text-xs text-[var(--ink-subtle)]">
                      剩余 {slot.availableQuota}/{slot.totalQuota}
                    </p>
                  </div>
                  <Badge tone="soft">¥{slot.fee}</Badge>
                </button>
              );
            })}
          </div>
        )}
      </Panel>
    </WorkspaceShell>
  );
}

function SummaryCard({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-[1.2rem] border border-[rgba(20,42,39,0.08)] bg-white/72 px-4 py-3">
      <p className="text-xs font-semibold uppercase tracking-[0.2em] text-[var(--ink-subtle)]">
        {label}
      </p>
      <p className="mt-2 text-sm font-semibold text-[var(--ink-strong)]">{value}</p>
    </div>
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
            <div className="grid gap-5 xl:grid-cols-2">
              <div className="h-64 animate-pulse rounded-[1.75rem] bg-slate-100" />
              <div className="h-64 animate-pulse rounded-[1.75rem] bg-slate-100" />
            </div>
          </div>
          <div className="space-y-5">
            <div className="h-48 animate-pulse rounded-[1.75rem] bg-slate-100" />
            <div className="h-64 animate-pulse rounded-[1.75rem] bg-slate-100" />
          </div>
        </div>
      </div>
    </main>
  );
}
