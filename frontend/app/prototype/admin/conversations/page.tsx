import {
  PrototypeBadge,
  PrototypePanel,
  PrototypeWorkspaceShell,
} from "@/components/prototype-workspace-shell";

const conversations = [
  "王女士 · 咳嗽挂什么科",
  "陈先生 · 查询周三上午号源",
  "李女士 · 取消上一条预约",
];

export default function PrototypeAdminConversationsPage() {
  return (
    <PrototypeWorkspaceShell
      role="admin"
      activeKey="conversations"
      title="会话排查"
      subtitle="按用户、会话和消息快速定位问题。"
      aside={
        <>
          <PrototypePanel title="会话摘要" eyebrow="Summary">
            <div className="rounded-[1.5rem] border border-[var(--line-soft)] bg-white/84 p-4 text-sm leading-7 text-[var(--ink-body)]">
              用户询问持续咳嗽挂号方向，系统建议呼吸内科，并继续返回周三上午号源。
            </div>
          </PrototypePanel>

          <PrototypePanel title="关键线索" eyebrow="Signals">
            <div className="flex flex-wrap gap-2">
              <PrototypeBadge>门诊挂号说明</PrototypeBadge>
              <PrototypeBadge>呼吸内科介绍</PrototypeBadge>
              <PrototypeBadge>queryScheduleSlots</PrototypeBadge>
            </div>
          </PrototypePanel>
        </>
      }
    >
      <div className="grid gap-5 xl:grid-cols-[0.92fr_1.08fr]">
        <PrototypePanel title="搜索与会话列表" eyebrow="Search">
          <div className="space-y-4">
            <div className="rounded-[1.45rem] border border-[var(--line-soft)] bg-white/82 px-4 py-3 text-sm text-[var(--ink-subtle)]">
              用户名 / 手机 / 邮箱 / 会话标题 / requestId
            </div>
            {conversations.map((item, index) => (
              <div
                key={item}
                className={`rounded-[1.5rem] border p-4 ${
                  index === 0 ? "border-[var(--accent-main)] bg-[var(--accent-warm-soft)]" : "border-[var(--line-soft)] bg-white/84"
                }`}
              >
                <p className="font-semibold text-[var(--ink-strong)]">{item}</p>
                <p className="mt-2 text-sm text-[var(--ink-subtle)]">最近活跃：10:42 · 3 轮消息</p>
              </div>
            ))}
          </div>
        </PrototypePanel>

        <PrototypePanel title="会话详情" eyebrow="Messages">
          <div className="space-y-4">
            <div className="rounded-[1.5rem] bg-[var(--accent-main)] px-4 py-4 text-sm leading-7 text-white">
              用户：我最近咳嗽一周，夜里加重，想看本周三上午有没有号。
            </div>
            <div className="rounded-[1.5rem] border border-[var(--line-soft)] bg-white/88 px-4 py-4 text-sm leading-7 text-[var(--ink-body)]">
              助手：建议优先看呼吸内科，并返回可预约号源与相关挂号规则。
            </div>
            <div className="rounded-[1.5rem] border border-[var(--line-soft)] bg-[var(--bg-soft)]/55 p-4 text-sm leading-7 text-[var(--ink-body)]">
              工具结果：查询到周三上午 3 条可预约号源。
            </div>
          </div>
        </PrototypePanel>
      </div>
    </PrototypeWorkspaceShell>
  );
}
