import {
  PrototypeBadge,
  PrototypePanel,
  PrototypeWorkspaceShell,
} from "@/components/prototype-workspace-shell";

const auditRows = [
  { time: "10:20", actor: "admin@medora", action: "发布知识文档", resource: "挂号须知.pdf" },
  { time: "10:42", actor: "王女士", action: "创建预约", resource: "呼吸内科 05-21 上午" },
  { time: "11:05", actor: "王女士", action: "取消预约", resource: "appointment_20260518_001" },
];

export default function PrototypeAdminAuditPage() {
  return (
    <PrototypeWorkspaceShell
      role="admin"
      activeKey="audit"
      title="审计日志"
      subtitle="关键写操作、资源对象与事件详情。"
      aside={
        <>
          <PrototypePanel title="筛选标签" eyebrow="Filters">
            <div className="flex flex-wrap gap-2">
              <PrototypeBadge>requestId</PrototypeBadge>
              <PrototypeBadge>用户名</PrototypeBadge>
              <PrototypeBadge>动作类型</PrototypeBadge>
              <PrototypeBadge>资源类型</PrototypeBadge>
            </div>
          </PrototypePanel>

          <PrototypePanel title="事件详情" eyebrow="Selected Event">
            <div className="rounded-[1.5rem] border border-[var(--line-soft)] bg-white/84 p-4 text-sm leading-7 text-[var(--ink-body)]">
              操作者：王女士
              <br />
              动作：创建预约
              <br />
              资源：呼吸内科 05-21 上午
              <br />
              requestId：req_20260518_001
            </div>
          </PrototypePanel>
        </>
      }
    >
      <div className="grid gap-5 xl:grid-cols-[0.95fr_1.05fr]">
        <PrototypePanel title="筛选区" eyebrow="Query">
          <div className="grid gap-3 md:grid-cols-2">
            {["requestId / 用户名 / 手机", "动作类型：预约 / 取消 / 发布", "资源类型：appointment / knowledge", "时间范围：近 24 小时"].map((item) => (
              <div
                key={item}
                className="rounded-[1.4rem] border border-[var(--line-soft)] bg-white/82 px-4 py-3 text-sm text-[var(--ink-subtle)]"
              >
                {item}
              </div>
            ))}
          </div>
        </PrototypePanel>

        <PrototypePanel title="事件列表" eyebrow="Events">
          <div className="space-y-3">
            {auditRows.map((row, index) => (
              <div
                key={`${row.time}-${row.actor}-${row.action}`}
                className={`rounded-[1.5rem] border p-4 ${
                  index === 1 ? "border-[var(--accent-main)] bg-[var(--accent-warm-soft)]" : "border-[var(--line-soft)] bg-white/84"
                }`}
              >
                <div className="flex flex-wrap items-center justify-between gap-3">
                  <div>
                    <p className="font-semibold text-[var(--ink-strong)]">
                      {row.actor} · {row.action}
                    </p>
                    <p className="mt-1 text-sm text-[var(--ink-subtle)]">{row.resource}</p>
                  </div>
                  <PrototypeBadge tone="solid">{row.time}</PrototypeBadge>
                </div>
              </div>
            ))}
          </div>
        </PrototypePanel>
      </div>
    </PrototypeWorkspaceShell>
  );
}
