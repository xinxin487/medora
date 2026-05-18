"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import type { FormEvent } from "react";
import { PatientShell } from "@/components/patient-shell";
import { ApiClientError, apiFetch } from "@/lib/api/client";
import type { PageResponse } from "@/lib/types";

type Department = {
  departmentId: string;
  code: string;
  name: string;
  category: string | null;
  description: string | null;
  status: string;
};

type Doctor = {
  doctorId: string;
  code: string;
  name: string;
  title: string | null;
  specialty: string | null;
  status: string;
  department: {
    departmentId: string;
    name: string;
  };
};

type ScheduleSlot = {
  slotId: string;
  scheduleDate: string;
  periodCode: string;
  startTime: string | null;
  endTime: string | null;
  totalQuota: number;
  availableQuota: number;
  fee: number;
  status: string;
  department: {
    departmentId: string;
    name: string;
  };
  doctor: {
    doctorId: string;
    name: string;
    title: string | null;
  };
};

type Appointment = {
  appointmentId: string;
  appointmentNo: string;
  status: string;
  bookedAt: string;
  scheduleDate: string;
  periodCode: string;
  departmentName: string;
  doctorName: string;
  fee: number;
};

type AppointmentCreated = {
  appointmentId: string;
  appointmentNo: string;
  status: string;
};

const tomorrow = new Date(Date.now() + 24 * 60 * 60 * 1000).toISOString().slice(0, 10);

export default function AppointmentsPage() {
  const [scheduleDate, setScheduleDate] = useState(tomorrow);
  const [periodCode, setPeriodCode] = useState("");
  const [departments, setDepartments] = useState<Department[]>([]);
  const [doctors, setDoctors] = useState<Doctor[]>([]);
  const [slots, setSlots] = useState<ScheduleSlot[]>([]);
  const [appointments, setAppointments] = useState<Appointment[]>([]);
  const [selectedDepartmentId, setSelectedDepartmentId] = useState("");
  const [selectedDoctorId, setSelectedDoctorId] = useState("");
  const [selectedSlotId, setSelectedSlotId] = useState("");
  const [message, setMessage] = useState("在这里完成从选科室到预约成功的完整动作。");
  const [error, setError] = useState<string | null>(null);
  const [isBusy, setIsBusy] = useState(false);

  const selectedDepartment =
    departments.find((item) => item.departmentId === selectedDepartmentId) ?? null;
  const selectedDoctor = doctors.find((item) => item.doctorId === selectedDoctorId) ?? null;
  const selectedSlot = slots.find((item) => item.slotId === selectedSlotId) ?? null;

  useEffect(() => {
    void loadDepartments();
    void loadAppointments();
  }, []);

  useEffect(() => {
    void loadDoctors(selectedDepartmentId);
  }, [selectedDepartmentId]);

  useEffect(() => {
    void loadSlots();
  }, [scheduleDate, selectedDepartmentId, selectedDoctorId, periodCode]);

  async function loadDepartments() {
    await runAction(async () => {
      const result = await apiFetch<PageResponse<Department>>("/departments?status=ACTIVE&pageSize=24");
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
      if (departmentId) {
        search.set("departmentId", departmentId);
      }
      const result = await apiFetch<PageResponse<Doctor>>(`/doctors?${search.toString()}`);
      setDoctors(result.list);
      setSelectedDoctorId((current) =>
        current && result.list.some((doctor) => doctor.doctorId === current)
          ? current
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
      if (selectedDepartmentId) {
        search.set("departmentId", selectedDepartmentId);
      }
      if (selectedDoctorId) {
        search.set("doctorId", selectedDoctorId);
      }
      if (periodCode) {
        search.set("periodCode", periodCode);
      }
      const result = await apiFetch<PageResponse<ScheduleSlot>>(`/schedule-slots?${search.toString()}`);
      setSlots(result.list);
      setSelectedSlotId((current) =>
        current && result.list.some((slot) => slot.slotId === current) ? current : result.list[0]?.slotId ?? "",
      );
      setMessage(`已更新 ${result.list.length} 个可预约号源。`);
    }, false);
  }

  async function loadAppointments() {
    await runAction(async () => {
      const result = await apiFetch<PageResponse<Appointment>>("/appointments?pageSize=20");
      setAppointments(result.list);
    }, false);
  }

  async function bookAppointment() {
    if (!selectedSlotId) {
      setError("请先从列表中选择一个号源。");
      return;
    }
    await runAction(async () => {
      const result = await apiFetch<AppointmentCreated>("/appointments", {
        method: "POST",
        headers: {
          "Idempotency-Key": crypto.randomUUID(),
        },
        body: JSON.stringify({
          slotId: selectedSlotId,
          remark: "正式预约中心页面创建",
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
        body: JSON.stringify({
          cancelReason: "用户在正式预约中心取消",
        }),
      });
      setMessage("预约已取消，号源已回补。");
      await loadSlots();
      await loadAppointments();
    });
  }

  async function runAction(action: () => Promise<void>, resetError = true) {
    setIsBusy(true);
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
    } finally {
      setIsBusy(false);
    }
  }

  return (
    <PatientShell
      eyebrow="Appointment Center"
      title="从选科室到锁定号源，把真实预约动作整理成一条更顺手的服务路径。"
      description="预约中心不再只是号源列表。它把科室、医生、时段、号源和我的预约组织到同一视图里，让用户更容易做决定。"
      actions={
        <>
          <button
            className="rounded-full bg-[var(--ink-strong)] px-5 py-3 text-sm font-semibold text-white disabled:opacity-60"
            disabled={isBusy || !selectedSlotId}
            onClick={() => void bookAppointment()}
            type="button"
          >
            预约当前号源
          </button>
          <Link
            href="/chat"
            className="rounded-full border border-[var(--accent-main)] bg-[rgba(31,92,82,0.08)] px-5 py-3 text-sm font-semibold text-[var(--accent-main)]"
          >
            先问导诊助手
          </Link>
        </>
      }
    >
      <div className="grid gap-5 xl:grid-cols-[1.06fr_0.94fr]">
        <section className="space-y-5">
          <form className="medora-surface border border-[var(--line-soft)] px-5 py-6" onSubmit={loadSlots}>
            <div className="flex flex-wrap items-start justify-between gap-4">
              <div>
                <p className="text-xs font-semibold uppercase tracking-[0.24em] text-[var(--accent-main)]">
                  Search Filters
                </p>
                <h2 className="mt-4 text-2xl font-semibold">筛选预约资源</h2>
              </div>
              <button
                className="rounded-full border border-[var(--line-soft)] px-4 py-2 text-sm font-semibold text-[var(--ink-body)] transition hover:border-[var(--accent-main)]"
                disabled={isBusy}
                type="submit"
              >
                更新号源
              </button>
            </div>

            <div className="mt-5 grid gap-4 md:grid-cols-3">
              <label className="block">
                <span className="text-sm font-semibold text-[var(--ink-body)]">就诊日期</span>
                <input
                  className="mt-2 w-full rounded-[1.1rem] border border-[var(--line-soft)] bg-white/80 px-4 py-3 text-sm outline-none transition focus:border-[var(--accent-main)]"
                  type="date"
                  value={scheduleDate}
                  onChange={(event) => setScheduleDate(event.target.value)}
                />
              </label>

              <label className="block">
                <span className="text-sm font-semibold text-[var(--ink-body)]">时段</span>
                <select
                  className="mt-2 w-full rounded-[1.1rem] border border-[var(--line-soft)] bg-white/80 px-4 py-3 text-sm outline-none transition focus:border-[var(--accent-main)]"
                  value={periodCode}
                  onChange={(event) => setPeriodCode(event.target.value)}
                >
                  <option value="">全天</option>
                  <option value="AM">上午</option>
                  <option value="PM">下午</option>
                </select>
              </label>

              <div className="rounded-[1.2rem] border border-[rgba(20,42,39,0.08)] bg-[rgba(237,243,238,0.56)] px-4 py-4">
                <p className="text-xs font-semibold uppercase tracking-[0.2em] text-[var(--ink-subtle)]">
                  当前可约
                </p>
                <p className="mt-3 text-2xl font-semibold text-[var(--ink-strong)]">{slots.length}</p>
              </div>
            </div>
          </form>

          <div className="medora-surface border border-[var(--line-soft)] px-5 py-6">
            <div className="flex flex-wrap items-center justify-between gap-3">
              <div>
                <p className="text-xs font-semibold uppercase tracking-[0.24em] text-[var(--ink-subtle)]">
                  Departments
                </p>
                <h2 className="mt-3 text-2xl font-semibold">先缩小就诊方向</h2>
              </div>
              <button
                className="rounded-full border border-[var(--line-soft)] px-4 py-2 text-sm font-semibold text-[var(--ink-body)]"
                disabled={isBusy}
                onClick={() => setSelectedDepartmentId("")}
                type="button"
              >
                查看全部科室
              </button>
            </div>

            <div className="mt-5 grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
              {departments.map((department) => {
                const active = department.departmentId === selectedDepartmentId;
                return (
                  <button
                    key={department.departmentId}
                    type="button"
                    onClick={() => setSelectedDepartmentId(department.departmentId)}
                    className={`rounded-[1.4rem] border px-4 py-4 text-left transition ${
                      active
                        ? "border-[var(--accent-main)] bg-[rgba(31,92,82,0.08)]"
                        : "border-[rgba(20,42,39,0.08)] bg-white/78 hover:border-[var(--accent-warm)]"
                    }`}
                  >
                    <p className="text-base font-semibold text-[var(--ink-strong)]">{department.name}</p>
                    <p className="mt-2 text-sm leading-6 text-[var(--ink-body)]">
                      {department.description || department.category || "常见门诊服务"}
                    </p>
                  </button>
                );
              })}
            </div>
          </div>

          <div className="medora-surface border border-[var(--line-soft)] px-5 py-6">
            <div className="flex flex-wrap items-center justify-between gap-3">
              <div>
                <p className="text-xs font-semibold uppercase tracking-[0.24em] text-[var(--ink-subtle)]">
                  Doctors
                </p>
                <h2 className="mt-3 text-2xl font-semibold">再看医生和擅长方向</h2>
              </div>
              <button
                className="rounded-full border border-[var(--line-soft)] px-4 py-2 text-sm font-semibold text-[var(--ink-body)]"
                disabled={isBusy}
                onClick={() => setSelectedDoctorId("")}
                type="button"
              >
                查看全部医生
              </button>
            </div>

            <div className="mt-5 grid gap-3">
              {doctors.length === 0 ? (
                <EmptyState text="当前条件下暂无医生信息。" />
              ) : (
                doctors.map((doctor) => {
                  const active = doctor.doctorId === selectedDoctorId;
                  return (
                    <button
                      key={doctor.doctorId}
                      type="button"
                      onClick={() => setSelectedDoctorId(doctor.doctorId)}
                      className={`grid gap-3 rounded-[1.4rem] border px-4 py-4 text-left transition md:grid-cols-[1fr_auto] ${
                        active
                          ? "border-[var(--accent-main)] bg-[rgba(31,92,82,0.08)]"
                          : "border-[rgba(20,42,39,0.08)] bg-white/78 hover:border-[var(--accent-warm)]"
                      }`}
                    >
                      <div>
                        <p className="text-base font-semibold text-[var(--ink-strong)]">
                          {doctor.name}
                          {doctor.title ? ` · ${doctor.title}` : ""}
                        </p>
                        <p className="mt-2 text-sm leading-6 text-[var(--ink-body)]">
                          {doctor.specialty || "门诊接诊与常规咨询"}
                        </p>
                      </div>
                      <div className="rounded-full bg-[rgba(184,132,81,0.08)] px-3 py-2 text-xs font-semibold text-[var(--accent-warm)]">
                        {doctor.department.name}
                      </div>
                    </button>
                  );
                })
              )}
            </div>
          </div>

          <div className="medora-surface border border-[var(--line-soft)] px-5 py-6">
            <div className="flex flex-wrap items-center justify-between gap-3">
              <div>
                <p className="text-xs font-semibold uppercase tracking-[0.24em] text-[var(--accent-main)]">
                  Schedule Slots
                </p>
                <h2 className="mt-3 text-2xl font-semibold">选择可预约号源</h2>
              </div>
              <p className="text-sm text-[var(--ink-subtle)]">{message}</p>
            </div>

            {error ? (
              <div className="mt-4 rounded-[1.2rem] border border-[rgba(190,92,67,0.18)] bg-[var(--danger-soft)] px-4 py-3 text-sm leading-6 text-[#8a4a3a]">
                {error}
              </div>
            ) : null}

            <div className="mt-5 grid gap-3">
              {slots.length === 0 ? (
                <EmptyState text="当前筛选条件下暂无开放号源，请调整日期、科室或时段。" />
              ) : (
                slots.map((slot) => {
                  const active = slot.slotId === selectedSlotId;
                  return (
                    <button
                      key={slot.slotId}
                      type="button"
                      onClick={() => setSelectedSlotId(slot.slotId)}
                      className={`grid gap-4 rounded-[1.5rem] border px-4 py-4 text-left transition md:grid-cols-[1fr_auto] ${
                        active
                          ? "border-[var(--accent-main)] bg-[rgba(31,92,82,0.08)]"
                          : "border-[rgba(20,42,39,0.08)] bg-white/80 hover:border-[var(--accent-warm)]"
                      }`}
                    >
                      <div>
                        <p className="text-base font-semibold text-[var(--ink-strong)]">
                          {slot.department.name} · {slot.doctor.name}
                        </p>
                        <p className="mt-2 text-sm leading-6 text-[var(--ink-body)]">
                          {slot.scheduleDate} · {formatPeriod(slot.periodCode)} · {slot.startTime ?? "--"} -{" "}
                          {slot.endTime ?? "--"}
                        </p>
                        <p className="mt-2 text-sm leading-6 text-[var(--ink-subtle)]">
                          剩余 {slot.availableQuota}/{slot.totalQuota} 个号源
                        </p>
                      </div>
                      <div className="flex items-center gap-3">
                        <span className="rounded-full bg-[rgba(184,132,81,0.08)] px-3 py-2 text-sm font-semibold text-[var(--accent-warm)]">
                          ¥{slot.fee}
                        </span>
                      </div>
                    </button>
                  );
                })
              )}
            </div>
          </div>
        </section>

        <aside className="space-y-5">
          <div className="medora-surface border border-[var(--line-soft)] bg-[linear-gradient(180deg,rgba(237,243,238,0.92),rgba(255,255,255,0.9))] px-5 py-6">
            <p className="text-xs font-semibold uppercase tracking-[0.24em] text-[var(--ink-subtle)]">
              Current Selection
            </p>
            <div className="mt-5 space-y-3">
              <SummaryCard label="科室" value={selectedDepartment?.name ?? "未限定"} />
              <SummaryCard
                label="医生"
                value={selectedDoctor ? `${selectedDoctor.name}${selectedDoctor.title ? ` · ${selectedDoctor.title}` : ""}` : "未限定"}
              />
              <SummaryCard label="日期" value={scheduleDate} />
              <SummaryCard label="时段" value={periodCode ? formatPeriod(periodCode) : "全天"} />
            </div>

            <div className="mt-5 rounded-[1.4rem] border border-[rgba(31,92,82,0.14)] bg-white/76 px-4 py-4">
              <p className="text-sm font-semibold text-[var(--accent-main)]">当前选中的号源</p>
              <p className="mt-3 text-sm leading-7 text-[var(--ink-body)]">
                {selectedSlot
                  ? `${selectedSlot.doctor.name} · ${selectedSlot.scheduleDate} · ${formatPeriod(selectedSlot.periodCode)}`
                  : "尚未选择号源。"}
              </p>
            </div>

            <button
              className="mt-5 w-full rounded-full bg-[var(--ink-strong)] px-5 py-3 text-sm font-semibold text-white disabled:opacity-60"
              disabled={isBusy || !selectedSlotId}
              onClick={() => void bookAppointment()}
              type="button"
            >
              预约当前选择
            </button>
          </div>

          <div className="medora-surface border border-[var(--line-soft)] px-5 py-6">
            <div className="flex flex-wrap items-center justify-between gap-3">
              <div>
                <p className="text-xs font-semibold uppercase tracking-[0.24em] text-[var(--accent-main)]">
                  My Appointments
                </p>
                <h2 className="mt-3 text-2xl font-semibold">我的预约</h2>
              </div>
              <button
                className="rounded-full border border-[var(--line-soft)] px-4 py-2 text-sm font-semibold text-[var(--ink-body)]"
                disabled={isBusy}
                onClick={() => void loadAppointments()}
                type="button"
              >
                刷新
              </button>
            </div>

            <div className="mt-5 grid gap-3">
              {appointments.length === 0 ? (
                <EmptyState text="当前账户还没有预约记录。" />
              ) : (
                appointments.map((appointment) => (
                  <article
                    key={appointment.appointmentId}
                    className="rounded-[1.4rem] border border-[rgba(20,42,39,0.08)] bg-white/78 px-4 py-4"
                  >
                    <div className="flex flex-wrap items-start justify-between gap-3">
                      <div>
                        <p className="text-base font-semibold text-[var(--ink-strong)]">
                          {appointment.departmentName} · {appointment.doctorName}
                        </p>
                        <p className="mt-2 text-sm leading-6 text-[var(--ink-body)]">
                          {appointment.scheduleDate} · {formatPeriod(appointment.periodCode)}
                        </p>
                        <p className="mt-2 text-xs uppercase tracking-[0.18em] text-[var(--ink-subtle)]">
                          {appointment.appointmentNo}
                        </p>
                      </div>
                      <span className="rounded-full bg-[rgba(31,92,82,0.08)] px-3 py-2 text-xs font-semibold text-[var(--accent-main)]">
                        {appointment.status}
                      </span>
                    </div>

                    <div className="mt-4 flex items-center justify-between gap-3">
                      <span className="text-sm font-semibold text-[var(--accent-warm)]">¥{appointment.fee}</span>
                      {appointment.status === "CONFIRMED" ? (
                        <button
                          className="rounded-full border border-[rgba(190,92,67,0.22)] bg-[var(--danger-soft)] px-4 py-2 text-sm font-semibold text-[#8a4a3a] disabled:opacity-60"
                          disabled={isBusy}
                          onClick={() => void cancelAppointment(appointment.appointmentId)}
                          type="button"
                        >
                          取消预约
                        </button>
                      ) : null}
                    </div>
                  </article>
                ))
              )}
            </div>
          </div>
        </aside>
      </div>
    </PatientShell>
  );
}

function SummaryCard({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-[1.2rem] border border-[rgba(20,42,39,0.08)] bg-white/72 px-4 py-4">
      <p className="text-xs font-semibold uppercase tracking-[0.2em] text-[var(--ink-subtle)]">{label}</p>
      <p className="mt-3 text-base font-semibold text-[var(--ink-strong)]">{value}</p>
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

function formatPeriod(value: string) {
  if (value === "AM") {
    return "上午";
  }
  if (value === "PM") {
    return "下午";
  }
  return value;
}
