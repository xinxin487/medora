import {
  PrototypeBadge,
  PrototypePanel,
  PrototypeWorkspaceShell,
} from "@/components/prototype-workspace-shell";

export default function PrototypePatientProfilePage() {
  return (
    <PrototypeWorkspaceShell
      role="patient"
      activeKey="profile"
      title="个人档案"
      subtitle="身份资料、默认就诊方向与服务偏好。"
      aside={
        <>
          <PrototypePanel title="账户状态" eyebrow="Account">
            <div className="space-y-3 text-sm text-[var(--ink-body)]">
              <div className="rounded-2xl border border-[var(--line-soft)] bg-white/82 px-4 py-3">当前角色：USER</div>
              <div className="rounded-2xl border border-[var(--line-soft)] bg-white/82 px-4 py-3">账号状态：正常</div>
            </div>
          </PrototypePanel>

          <PrototypePanel title="快捷偏好" eyebrow="Quick Tags">
            <div className="flex flex-wrap gap-2">
              <PrototypeBadge>上午优先</PrototypeBadge>
              <PrototypeBadge>工作日就诊</PrototypeBadge>
              <PrototypeBadge>呼吸内科</PrototypeBadge>
            </div>
          </PrototypePanel>
        </>
      }
    >
      <div className="grid gap-5 xl:grid-cols-[1fr_1fr]">
        <PrototypePanel title="基础资料" eyebrow="Identity">
          <div className="grid gap-3">
            {["姓名：王女士", "手机号：138****0000", "邮箱：medora@example.com"].map((item) => (
              <div
                key={item}
                className="rounded-[1.4rem] border border-[var(--line-soft)] bg-white/84 px-4 py-3.5 text-sm text-[var(--ink-body)]"
              >
                {item}
              </div>
            ))}
          </div>
        </PrototypePanel>

        <PrototypePanel title="就诊偏好" eyebrow="Care Preferences">
          <div className="space-y-3">
            <div className="rounded-[1.5rem] border border-[var(--line-soft)] bg-[var(--accent-warm-soft)] p-4">
              <p className="font-semibold text-[var(--ink-strong)]">默认科室</p>
              <p className="mt-2 text-lg font-semibold text-[var(--accent-main-strong)]">呼吸内科</p>
            </div>
            <div className="rounded-[1.5rem] border border-[var(--line-soft)] bg-white/84 p-4">
              <p className="font-semibold text-[var(--ink-strong)]">默认医院编码</p>
              <p className="mt-2 text-sm text-[var(--ink-body)]">MEDORA-HOSPITAL-001</p>
            </div>
            <div className="rounded-[1.5rem] border border-[var(--line-soft)] bg-white/84 p-4">
              <p className="font-semibold text-[var(--ink-strong)]">常用时段</p>
              <p className="mt-2 text-sm text-[var(--ink-body)]">上午优先</p>
            </div>
          </div>
        </PrototypePanel>
      </div>
    </PrototypeWorkspaceShell>
  );
}
