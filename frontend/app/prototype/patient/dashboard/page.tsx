import Link from "next/link";
import {
  PrototypeMetricCard,
  PrototypePanel,
  PrototypeWorkspaceShell,
} from "@/components/prototype-workspace-shell";

const nextSteps = [
  {
    title: "继续导诊",
    body: "接着上次关于咳嗽和挂号方向的会话继续提问。",
    href: "/prototype/patient/assistant",
  },
  {
    title: "查看号源",
    body: "按本周时间与默认科室快速筛选可预约号源。",
    href: "/prototype/patient/appointments",
  },
  {
    title: "回看服务",
    body: "查看最近预约、取消记录和就诊准备事项。",
    href: "/prototype/patient/services",
  },
];

export default function PrototypePatientDashboardPage() {
  return (
    <PrototypeWorkspaceShell
      role="patient"
      activeKey="dashboard"
      title="下午好，王女士"
      subtitle="你本周有 1 个已确认预约，最近一次导诊仍可继续。"
      actions={
        <>
          <Link
            href="/prototype/patient/assistant"
            className="rounded-full bg-[var(--accent-main)] px-5 py-3 text-sm font-semibold text-white transition hover:bg-[var(--accent-main-strong)]"
          >
            继续导诊
          </Link>
          <Link
            href="/prototype/patient/appointments"
            className="rounded-full border border-[var(--line-soft)] bg-white px-5 py-3 text-sm font-semibold text-[var(--ink-strong)] transition hover:border-[var(--accent-main)] hover:text-[var(--accent-main)]"
          >
            查看号源
          </Link>
        </>
      }
      aside={
        <>
          <PrototypePanel title="本周预约" eyebrow="This Week">
            <div className="rounded-[1.5rem] border border-[var(--line-soft)] bg-[var(--accent-warm-soft)] p-4">
              <p className="text-lg font-semibold text-[var(--ink-strong)]">05 月 21 日 · 上午</p>
              <p className="mt-2 text-sm text-[var(--ink-body)]">呼吸内科 · 李医生</p>
              <p className="mt-1 text-sm text-[var(--ink-subtle)]">预约号 M202605210018</p>
            </div>
          </PrototypePanel>

          <PrototypePanel title="常用偏好" eyebrow="Preferences">
            <div className="space-y-3 text-sm text-[var(--ink-body)]">
              <div className="rounded-2xl border border-[var(--line-soft)] bg-white/82 px-4 py-3">默认科室：呼吸内科</div>
              <div className="rounded-2xl border border-[var(--line-soft)] bg-white/82 px-4 py-3">偏好时段：上午</div>
            </div>
          </PrototypePanel>

          <PrototypePanel title="最近提醒" eyebrow="Notices">
            <div className="space-y-3 text-sm text-[var(--ink-body)]">
              <div className="rounded-2xl border border-[var(--line-soft)] bg-white/82 px-4 py-3">就诊前一天可再次确认号源与到院时间。</div>
              <div className="rounded-2xl border border-[var(--line-soft)] bg-white/82 px-4 py-3">上次导诊已保留会话上下文，可直接续聊。</div>
            </div>
          </PrototypePanel>
        </>
      }
    >
      <div className="grid gap-5 md:grid-cols-3">
        <PrototypeMetricCard label="进行中服务" value="2" hint="1 个已确认预约，1 个可继续导诊主题。" />
        <PrototypeMetricCard label="最近预约" value="呼吸内科" hint="李医生 · 05-21 上午" />
        <PrototypeMetricCard label="连续会话" value="3 轮" hint="最近一次问诊建议仍可直接续接。" />
      </div>

      <div className="grid gap-5 xl:grid-cols-[1.06fr_0.94fr]">
        <PrototypePanel title="下一步">
          <div className="space-y-3">
            {nextSteps.map((step) => (
              <Link
                key={step.title}
                href={step.href}
                className="block rounded-[1.5rem] border border-[var(--line-soft)] bg-white/84 p-4 transition hover:border-[var(--accent-main)] hover:bg-white"
              >
                <div className="flex items-center justify-between gap-3">
                  <h3 className="text-lg font-semibold text-[var(--ink-strong)]">{step.title}</h3>
                  <span className="text-sm font-semibold text-[var(--accent-main)]">进入</span>
                </div>
                <p className="mt-2 text-sm leading-6 text-[var(--ink-body)]">{step.body}</p>
              </Link>
            ))}
          </div>
        </PrototypePanel>

        <PrototypePanel title="近期服务">
          <div className="space-y-4">
            <div className="rounded-[1.6rem] border border-[var(--line-soft)] bg-white/84 p-4">
              <div className="flex items-center justify-between gap-3">
                <p className="text-sm font-semibold text-[var(--ink-strong)]">导诊助手</p>
                <span className="rounded-full bg-[var(--accent-warm-soft)] px-3 py-1 text-xs font-semibold text-[var(--accent-main-strong)]">
                  可继续
                </span>
              </div>
              <p className="mt-3 text-sm leading-7 text-[var(--ink-body)]">
                “我最近咳嗽一周，夜里加重，应该先挂哪个科？”
              </p>
            </div>

            <div className="rounded-[1.6rem] border border-[var(--line-soft)] bg-white/84 p-4">
              <div className="flex items-center justify-between gap-3">
                <p className="text-sm font-semibold text-[var(--ink-strong)]">预约服务</p>
                <span className="rounded-full bg-emerald-50 px-3 py-1 text-xs font-semibold text-emerald-800">
                  已确认
                </span>
              </div>
              <p className="mt-3 text-sm leading-7 text-[var(--ink-body)]">
                05 月 21 日上午 · 呼吸内科 · 李医生
              </p>
            </div>
          </div>
        </PrototypePanel>
      </div>
    </PrototypeWorkspaceShell>
  );
}
