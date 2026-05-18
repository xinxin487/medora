import {
  PrototypeBadge,
  PrototypePanel,
  PrototypeWorkspaceShell,
} from "@/components/prototype-workspace-shell";

const traceSteps = [
  { name: "Intent Router", body: "导诊 + 查号源", time: "42ms" },
  { name: "Retrieval", body: "命中挂号规则与呼吸内科说明", time: "118ms" },
  { name: "Tool Call", body: "queryScheduleSlots 返回 3 条可约号源", time: "93ms" },
  { name: "Model Response", body: "生成最终回复与下一步建议", time: "1640ms" },
];

export default function PrototypeAdminTracesPage() {
  return (
    <PrototypeWorkspaceShell
      role="admin"
      activeKey="traces"
      title="Trace"
      subtitle="查看意图、检索、工具和模型响应的完整链路。"
      aside={
        <>
          <PrototypePanel title="链路标识" eyebrow="Trace Info">
            <div className="flex flex-wrap gap-2">
              <PrototypeBadge>trace_xxx</PrototypeBadge>
              <PrototypeBadge>conv_xxx</PrototypeBadge>
              <PrototypeBadge>msg_srv_001</PrototypeBadge>
            </div>
          </PrototypePanel>

          <PrototypePanel title="关键结果" eyebrow="Result">
            <div className="space-y-3 text-sm text-[var(--ink-body)]">
              <div className="rounded-2xl border border-[var(--line-soft)] bg-white/82 px-4 py-3">推荐科室：呼吸内科</div>
              <div className="rounded-2xl border border-[var(--line-soft)] bg-white/82 px-4 py-3">返回号源：3 条</div>
            </div>
          </PrototypePanel>
        </>
      }
    >
      <PrototypePanel title="链路时间线" eyebrow="Timeline">
        <div className="space-y-4">
          {traceSteps.map((step, index) => (
            <div
              key={step.name}
              className="rounded-[1.5rem] border border-[var(--line-soft)] bg-white/84 p-4"
            >
              <div className="flex flex-wrap items-center justify-between gap-3">
                <div className="flex items-center gap-3">
                  <span className="rounded-full bg-[var(--accent-warm-soft)] px-3 py-1 text-xs font-semibold text-[var(--accent-main-strong)]">
                    {String(index + 1).padStart(2, "0")}
                  </span>
                  <h3 className="text-lg font-semibold text-[var(--ink-strong)]">{step.name}</h3>
                </div>
                <PrototypeBadge tone="solid">{step.time}</PrototypeBadge>
              </div>
              <p className="mt-3 text-sm leading-6 text-[var(--ink-body)]">{step.body}</p>
            </div>
          ))}
        </div>
      </PrototypePanel>
    </PrototypeWorkspaceShell>
  );
}
