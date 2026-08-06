import {
  PrototypeBadge,
  PrototypePanel,
  PrototypeWorkspaceShell,
} from "@/components/prototype-workspace-shell";

const departments = ["呼吸内科", "消化内科", "心血管内科", "全科门诊"];

const doctors = [
  { name: "李医生", title: "主任医师", specialty: "慢性咳嗽 / 肺部感染" },
  { name: "周医生", title: "副主任医师", specialty: "哮喘 / 过敏性呼吸问题" },
  { name: "陈医生", title: "主治医师", specialty: "普通门诊 / 初诊评估" },
];

const slots = [
  { date: "05-21", period: "上午", time: "08:30 - 11:30", fee: "30 元", quota: "剩余 4" },
  { date: "05-21", period: "下午", time: "14:00 - 17:00", fee: "30 元", quota: "剩余 2" },
  { date: "05-22", period: "上午", time: "08:30 - 11:30", fee: "30 元", quota: "剩余 6" },
];

export default function PrototypePatientAppointmentsPage() {
  return (
    <PrototypeWorkspaceShell
      role="patient"
      activeKey="appointments"
      title="预约服务"
      subtitle="从科室筛选到锁定号源，在同一页面完成选择与确认。"
      actions={
        <PrototypeBadge tone="soft">已根据默认科室优先展示呼吸内科</PrototypeBadge>
      }
      aside={
        <>
          <PrototypePanel title="当前选择" eyebrow="Selection">
            <div className="space-y-3 text-sm text-[var(--ink-body)]">
              <div className="rounded-2xl border border-[var(--line-soft)] bg-white/82 px-4 py-3">科室：呼吸内科</div>
              <div className="rounded-2xl border border-[var(--line-soft)] bg-white/82 px-4 py-3">医生：李医生</div>
              <div className="rounded-[1.5rem] bg-[var(--accent-main)] px-4 py-4 text-white">
                <p className="font-semibold">05-21 上午</p>
                <p className="mt-2 text-sm text-white/82">08:30 - 11:30 · 挂号费 30 元</p>
              </div>
            </div>
          </PrototypePanel>

          <PrototypePanel title="预约确认" eyebrow="Booking">
            <div className="space-y-3">
              <button className="w-full rounded-[1.3rem] bg-[var(--accent-main)] px-4 py-3 text-sm font-semibold text-white shadow-[0_16px_36px_rgba(31,92,82,0.18)]">
                预约当前号源
              </button>
              <div className="rounded-2xl border border-[var(--line-soft)] bg-white/82 px-4 py-3 text-sm leading-6 text-[var(--ink-body)]">
                本页下方可继续查看我的预约与取消记录。
              </div>
            </div>
          </PrototypePanel>
        </>
      }
    >
      <PrototypePanel title="筛选条件" eyebrow="Filters">
        <div className="flex flex-wrap gap-2">
          <PrototypeBadge tone="soft">日期：2026-05-21</PrototypeBadge>
          <PrototypeBadge tone="soft">时段：上午优先</PrototypeBadge>
          <PrototypeBadge tone="soft">仅看可预约</PrototypeBadge>
          <PrototypeBadge tone="soft">默认科室优先</PrototypeBadge>
        </div>
      </PrototypePanel>

      <div className="grid gap-5 xl:grid-cols-[1.06fr_0.94fr]">
        <div className="space-y-5">
          <PrototypePanel title="科室">
            <div className="grid gap-3 md:grid-cols-2">
              {departments.map((department, index) => (
                <div
                  key={department}
                  className={`rounded-[1.5rem] border p-4 ${
                    index === 0 ? "border-[var(--accent-main)] bg-[var(--accent-warm-soft)]" : "border-[var(--line-soft)] bg-white/82"
                  }`}
                >
                  <p className="text-lg font-semibold text-[var(--ink-strong)]">{department}</p>
                </div>
              ))}
            </div>
          </PrototypePanel>

          <PrototypePanel title="医生">
            <div className="space-y-3">
              {doctors.map((doctor, index) => (
                <div
                  key={doctor.name}
                  className={`rounded-[1.5rem] border p-4 ${
                    index === 0 ? "border-[var(--accent-main)] bg-white" : "border-[var(--line-soft)] bg-white/82"
                  }`}
                >
                  <div className="flex flex-wrap items-center justify-between gap-3">
                    <div>
                      <p className="text-lg font-semibold text-[var(--ink-strong)]">{doctor.name}</p>
                      <p className="mt-1 text-sm text-[var(--ink-subtle)]">{doctor.title}</p>
                    </div>
                    <PrototypeBadge>{doctor.specialty}</PrototypeBadge>
                  </div>
                </div>
              ))}
            </div>
          </PrototypePanel>
        </div>

        <PrototypePanel title="可预约号源" eyebrow="Available Slots">
          <div className="space-y-3">
            {slots.map((slot, index) => (
              <div
                key={`${slot.date}-${slot.period}`}
                className={`rounded-[1.5rem] border p-4 ${
                  index === 0 ? "border-[var(--accent-main)] bg-white" : "border-[var(--line-soft)] bg-white/82"
                }`}
              >
                <div className="flex flex-wrap items-center justify-between gap-3">
                  <div>
                    <p className="text-lg font-semibold text-[var(--ink-strong)]">
                      {slot.date} · {slot.period}
                    </p>
                    <p className="mt-1 text-sm text-[var(--ink-subtle)]">{slot.time}</p>
                  </div>
                  <PrototypeBadge tone={index === 0 ? "solid" : "default"}>{slot.quota}</PrototypeBadge>
                </div>
                <div className="mt-3 flex flex-wrap items-center justify-between gap-3 text-sm text-[var(--ink-body)]">
                  <span>挂号费：{slot.fee}</span>
                  <span className="font-semibold text-[var(--accent-main)]">选择</span>
                </div>
              </div>
            ))}
          </div>
        </PrototypePanel>
      </div>
    </PrototypeWorkspaceShell>
  );
}
