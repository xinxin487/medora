import {
  PrototypeMetricCard,
  PrototypePanel,
  PrototypeWorkspaceShell,
} from "@/components/prototype-workspace-shell";

const records = [
  {
    date: "05-18",
    title: "呼吸内科预约已确认",
    detail: "05-21 上午 · 李医生 · 预约号 M202605210018",
    status: "已确认",
  },
  {
    date: "05-18",
    title: "导诊会话已保存",
    detail: "咳嗽与挂号方向建议已保留，可直接续聊。",
    status: "可继续",
  },
  {
    date: "05-12",
    title: "上一条预约已取消",
    detail: "消化内科 · 取消已完成，号源已回补。",
    status: "已取消",
  },
];

export default function PrototypePatientServicesPage() {
  return (
    <PrototypeWorkspaceShell
      role="patient"
      activeKey="services"
      title="我的服务"
      subtitle="近期预约、导诊和历史操作统一整理在这里。"
      aside={
        <>
          <PrototypePanel title="近期提醒" eyebrow="Reminders">
            <div className="space-y-3 text-sm text-[var(--ink-body)]">
              <div className="rounded-2xl border border-[var(--line-soft)] bg-white/82 px-4 py-3">到院前可再次确认挂号时间与路线。</div>
              <div className="rounded-2xl border border-[var(--line-soft)] bg-white/82 px-4 py-3">如需改期，可从预约记录继续发起取消。</div>
            </div>
          </PrototypePanel>

          <PrototypePanel title="服务偏好" eyebrow="Preferences">
            <div className="space-y-3 text-sm text-[var(--ink-body)]">
              <div className="rounded-2xl border border-[var(--line-soft)] bg-white/82 px-4 py-3">默认科室：呼吸内科</div>
              <div className="rounded-2xl border border-[var(--line-soft)] bg-white/82 px-4 py-3">就诊时段：上午优先</div>
            </div>
          </PrototypePanel>
        </>
      }
    >
      <div className="grid gap-5 md:grid-cols-3">
        <PrototypeMetricCard label="进行中预约" value="1" hint="本周三上午仍然有效。" />
        <PrototypeMetricCard label="最近会话" value="1 条" hint="可直接回到导诊助手继续提问。" />
        <PrototypeMetricCard label="历史取消" value="2 条" hint="所有变更都可回看。" />
      </div>

      <PrototypePanel title="服务记录" eyebrow="Timeline">
        <div className="space-y-4">
          {records.map((record) => (
            <div
              key={`${record.date}-${record.title}`}
              className="rounded-[1.6rem] border border-[var(--line-soft)] bg-white/84 p-4"
            >
              <div className="flex flex-wrap items-center justify-between gap-3">
                <div className="flex items-center gap-3">
                  <span className="rounded-full bg-[var(--accent-warm-soft)] px-3 py-1 text-xs font-semibold text-[var(--accent-main-strong)]">
                    {record.date}
                  </span>
                  <h3 className="text-lg font-semibold text-[var(--ink-strong)]">{record.title}</h3>
                </div>
                <span className="rounded-full border border-[var(--line-soft)] bg-white px-3 py-1 text-xs font-semibold text-[var(--ink-body)]">
                  {record.status}
                </span>
              </div>
              <p className="mt-3 text-sm leading-6 text-[var(--ink-body)]">{record.detail}</p>
            </div>
          ))}
        </div>
      </PrototypePanel>
    </PrototypeWorkspaceShell>
  );
}
