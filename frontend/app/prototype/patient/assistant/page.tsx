import Link from "next/link";
import {
  PrototypeBadge,
  PrototypePanel,
  PrototypeWorkspaceShell,
} from "@/components/prototype-workspace-shell";

const chatMessages = [
  {
    role: "用户",
    content: "我最近咳嗽一周，夜里会加重，应该先挂哪个科？",
  },
  {
    role: "助手",
    content: "可以优先考虑呼吸内科。我已经整理了相关挂号规则，也可以继续帮你看本周可预约号源。",
  },
  {
    role: "用户",
    content: "那帮我看看周三上午还有没有号。",
  },
  {
    role: "助手",
    content: "周三上午还有 3 个可预约号源，李医生和周医生都可选。你也可以直接切到预约服务页继续确认。",
  },
];

export default function PrototypePatientAssistantPage() {
  return (
    <PrototypeWorkspaceShell
      role="patient"
      activeKey="assistant"
      title="导诊助手"
      subtitle="会话、知识依据与后续服务动作放在同一工作区。"
      actions={
        <Link
          href="/prototype/patient/appointments"
          className="rounded-full bg-[var(--accent-main)] px-5 py-3 text-sm font-semibold text-white transition hover:bg-[var(--accent-main-strong)]"
        >
          去预约服务
        </Link>
      }
      aside={
        <>
          <PrototypePanel title="知识依据" eyebrow="References">
            <div className="space-y-3">
              <div className="rounded-[1.5rem] border border-[var(--line-soft)] bg-white/84 p-4">
                <p className="font-semibold text-[var(--ink-strong)]">门诊挂号说明</p>
                <p className="mt-2 text-sm leading-6 text-[var(--ink-body)]">
                  普通门诊可提前预约，取消规则以系统实际返回为准。
                </p>
              </div>
              <div className="rounded-[1.5rem] border border-[var(--line-soft)] bg-white/84 p-4">
                <p className="font-semibold text-[var(--ink-strong)]">呼吸内科介绍</p>
                <p className="mt-2 text-sm leading-6 text-[var(--ink-body)]">
                  适合持续咳嗽、呼吸道感染和哮喘相关问题。
                </p>
              </div>
            </div>
          </PrototypePanel>

          <PrototypePanel title="快捷动作" eyebrow="Actions">
            <div className="flex flex-wrap gap-2">
              <PrototypeBadge>查看周三上午号源</PrototypeBadge>
              <PrototypeBadge>显示我的预约记录</PrototypeBadge>
              <PrototypeBadge>挂号前准备事项</PrototypeBadge>
              <PrototypeBadge>如何取消预约</PrototypeBadge>
            </div>
          </PrototypePanel>
        </>
      }
    >
      <div className="grid gap-5 xl:grid-cols-[1.1fr_0.9fr]">
        <PrototypePanel title="会话">
          <div className="flex flex-wrap gap-2">
            <PrototypeBadge tone="soft">当前主题：呼吸内科建议</PrototypeBadge>
            <PrototypeBadge tone="soft">偏好时段：上午</PrototypeBadge>
          </div>

          <div className="mt-4 space-y-4">
            {chatMessages.map((message) => (
              <div
                key={`${message.role}-${message.content}`}
                className={`rounded-[1.6rem] px-4 py-4 ${
                  message.role === "助手"
                    ? "border border-[var(--line-soft)] bg-white/90"
                    : "bg-[var(--accent-main)] text-white"
                }`}
              >
                <p className={`text-xs font-semibold uppercase tracking-[0.2em] ${
                  message.role === "助手" ? "text-[var(--ink-subtle)]" : "text-white/72"
                }`}>
                  {message.role}
                </p>
                <p className={`mt-2 text-sm leading-7 ${
                  message.role === "助手" ? "text-[var(--ink-body)]" : "text-white"
                }`}>
                  {message.content}
                </p>
              </div>
            ))}
          </div>

          <div className="mt-4 rounded-[1.6rem] border border-[var(--line-soft)] bg-white/84 px-4 py-4 text-sm text-[var(--ink-subtle)]">
            输入症状、挂号问题、号源查询请求或取消需求……
          </div>
        </PrototypePanel>

        <PrototypePanel title="服务联动" eyebrow="Next Step">
          <div className="space-y-4">
            <div className="rounded-[1.6rem] border border-[var(--line-soft)] bg-[var(--accent-warm-soft)] p-4">
              <p className="text-sm font-semibold text-[var(--ink-strong)]">推荐科室</p>
              <p className="mt-2 text-lg font-semibold text-[var(--accent-main-strong)]">呼吸内科</p>
            </div>
            <div className="rounded-[1.6rem] border border-[var(--line-soft)] bg-white/84 p-4">
              <p className="text-sm font-semibold text-[var(--ink-strong)]">可预约时段</p>
              <p className="mt-2 text-sm leading-6 text-[var(--ink-body)]">
                周三上午剩余 3 个号源，周三下午剩余 2 个号源。
              </p>
            </div>
            <Link
              href="/prototype/patient/appointments"
              className="block rounded-[1.35rem] bg-[var(--accent-main)] px-4 py-3.5 text-center text-sm font-semibold text-white shadow-[0_16px_36px_rgba(31,92,82,0.18)]"
            >
              进入预约服务
            </Link>
          </div>
        </PrototypePanel>
      </div>
    </PrototypeWorkspaceShell>
  );
}
