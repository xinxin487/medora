"use client";

import Link from "next/link";
import { FormEvent, useEffect, useState } from "react";
import { ApiClientError, apiFetch } from "@/lib/api/client";
import type { PageResponse } from "@/lib/types";

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
  const [slots, setSlots] = useState<ScheduleSlot[]>([]);
  const [appointments, setAppointments] = useState<Appointment[]>([]);
  const [selectedSlotId, setSelectedSlotId] = useState("");
  const [message, setMessage] = useState("准备验证 Phase 3 预约闭环。");
  const [error, setError] = useState<string | null>(null);
  const [isBusy, setIsBusy] = useState(false);

  useEffect(() => {
    void loadAppointments();
  }, []);

  async function loadSlots(event?: FormEvent<HTMLFormElement>) {
    event?.preventDefault();
    await runAction(async () => {
      const result = await apiFetch<PageResponse<ScheduleSlot>>(
        `/schedule-slots?scheduleDate=${encodeURIComponent(scheduleDate)}&status=OPEN&pageSize=50`,
      );
      setSlots(result.list);
      setSelectedSlotId(result.list[0]?.slotId ?? "");
      setMessage(`已读取 ${result.list.length} 条可预约号源。`);
    });
  }

  async function loadAppointments() {
    await runAction(async () => {
      const result = await apiFetch<PageResponse<Appointment>>("/appointments?pageSize=50");
      setAppointments(result.list);
      setMessage(`已读取 ${result.list.length} 条我的预约。`);
    }, false);
  }

  async function bookAppointment() {
    if (!selectedSlotId) {
      setError("请先选择一个号源。");
      return;
    }
    await runAction(async () => {
      const result = await apiFetch<AppointmentCreated>(
        "/appointments",
        {
          method: "POST",
          headers: {
            "Idempotency-Key": crypto.randomUUID(),
          },
          body: JSON.stringify({
            slotId: selectedSlotId,
            remark: "Phase 3 页面联调创建",
          }),
        },
      );
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
          cancelReason: "Phase 3 页面联调取消",
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
    <main className="min-h-screen px-6 py-8 text-slate-950">
      <section className="mx-auto grid max-w-7xl gap-6 xl:grid-cols-[380px_1fr]">
        <aside className="space-y-5">
          <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
            <Link href="/" className="text-sm font-semibold text-emerald-700">
              返回首页
            </Link>
            <h1 className="mt-5 text-3xl font-semibold leading-tight">
              预约闭环联调
            </h1>
            <p className="mt-4 text-sm leading-7 text-slate-600">
              查询号源，创建预约，再取消预约，验证数据库事务里的扣减和回补。
            </p>
          </div>

          <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
            <p className="text-xs font-semibold uppercase tracking-[0.2em] text-slate-500">
              Status
            </p>
            <p className="mt-3 text-sm leading-7 text-slate-700">{message}</p>
            {error ? (
              <p className="mt-3 rounded-xl border border-rose-200 bg-rose-50 px-3 py-2 text-sm text-rose-700">
                {error}
              </p>
            ) : null}
          </div>

          <form className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm" onSubmit={loadSlots}>
            <label className="text-sm font-semibold text-slate-700" htmlFor="scheduleDate">
              就诊日期
            </label>
            <input
              id="scheduleDate"
              type="date"
              className="mt-3 w-full rounded-xl border border-slate-200 px-3 py-2 text-sm outline-none focus:border-emerald-500"
              value={scheduleDate}
              onChange={(event) => setScheduleDate(event.target.value)}
            />
            <button
              className="mt-4 w-full rounded-xl bg-slate-950 px-4 py-3 text-sm font-semibold text-white disabled:opacity-60"
              disabled={isBusy}
              type="submit"
            >
              查询号源
            </button>
          </form>
        </aside>

        <div className="grid gap-6">
          <section className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
            <div className="flex flex-wrap items-center justify-between gap-3">
              <h2 className="text-xl font-semibold">可预约号源</h2>
              <button
                className="rounded-xl bg-emerald-700 px-4 py-2 text-sm font-semibold text-white disabled:opacity-60"
                disabled={isBusy || !selectedSlotId}
                onClick={bookAppointment}
                type="button"
              >
                预约选中号源
              </button>
            </div>

            <div className="mt-5 grid gap-3">
              {slots.length === 0 ? (
                <p className="rounded-xl bg-slate-50 px-4 py-5 text-sm text-slate-500">
                  暂无号源，请换一个日期查询。
                </p>
              ) : (
                slots.map((slot) => (
                  <label
                    key={slot.slotId}
                    className="grid cursor-pointer gap-3 rounded-xl border border-slate-200 p-4 text-sm transition hover:border-emerald-400 md:grid-cols-[24px_1fr_auto]"
                  >
                    <input
                      checked={selectedSlotId === slot.slotId}
                      className="mt-1"
                      name="slot"
                      onChange={() => setSelectedSlotId(slot.slotId)}
                      type="radio"
                    />
                    <span>
                      <span className="block font-semibold text-slate-900">
                        {slot.department.name} · {slot.doctor.name} · {slot.periodCode}
                      </span>
                      <span className="mt-1 block text-slate-500">
                        {slot.scheduleDate} {slot.startTime ?? ""}-{slot.endTime ?? ""} · 剩余 {slot.availableQuota}/{slot.totalQuota}
                      </span>
                    </span>
                    <span className="font-semibold text-slate-800">¥{slot.fee}</span>
                  </label>
                ))
              )}
            </div>
          </section>

          <section className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
            <div className="flex flex-wrap items-center justify-between gap-3">
              <h2 className="text-xl font-semibold">我的预约</h2>
              <button
                className="rounded-xl border border-slate-200 px-4 py-2 text-sm font-semibold text-slate-700 disabled:opacity-60"
                disabled={isBusy}
                onClick={() => void loadAppointments()}
                type="button"
              >
                刷新列表
              </button>
            </div>

            <div className="mt-5 grid gap-3">
              {appointments.length === 0 ? (
                <p className="rounded-xl bg-slate-50 px-4 py-5 text-sm text-slate-500">
                  当前账号还没有预约。
                </p>
              ) : (
                appointments.map((appointment) => (
                  <div
                    key={appointment.appointmentId}
                    className="grid gap-3 rounded-xl border border-slate-200 p-4 text-sm md:grid-cols-[1fr_auto]"
                  >
                    <div>
                      <p className="font-semibold text-slate-900">
                        {appointment.departmentName} · {appointment.doctorName}
                      </p>
                      <p className="mt-1 text-slate-500">
                        {appointment.appointmentNo} · {appointment.scheduleDate} · {appointment.periodCode} · {appointment.status}
                      </p>
                    </div>
                    <button
                      className="rounded-xl border border-rose-200 px-4 py-2 font-semibold text-rose-700 disabled:opacity-50"
                      disabled={isBusy || appointment.status !== "CONFIRMED"}
                      onClick={() => void cancelAppointment(appointment.appointmentId)}
                      type="button"
                    >
                      取消预约
                    </button>
                  </div>
                ))
              )}
            </div>
          </section>
        </div>
      </section>
    </main>
  );
}
