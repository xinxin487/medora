import {
  PrototypeBadge,
  PrototypePanel,
  PrototypeWorkspaceShell,
} from "@/components/prototype-workspace-shell";

const docs = [
  { name: "挂号须知.pdf", status: "已发布", time: "10:20" },
  { name: "呼吸内科介绍.docx", status: "待重建", time: "09:35" },
  { name: "门诊取消规则.md", status: "解析失败", time: "昨天" },
];

export default function PrototypeAdminKnowledgePage() {
  return (
    <PrototypeWorkspaceShell
      role="admin"
      activeKey="knowledge"
      title="知识库"
      subtitle="文档接入、状态管理与检索验证。"
      aside={
        <>
          <PrototypePanel title="检索测试" eyebrow="Search Test">
            <div className="space-y-3">
              <div className="rounded-2xl border border-[var(--line-soft)] bg-white/82 px-4 py-3 text-sm text-[var(--ink-subtle)]">
                挂号可以取消吗？
              </div>
              <div className="rounded-[1.5rem] border border-[var(--line-soft)] bg-[var(--accent-warm-soft)] p-4">
                <p className="font-semibold text-[var(--ink-strong)]">命中结果</p>
                <p className="mt-2 text-sm leading-6 text-[var(--ink-body)]">
                  如需取消挂号，请在就诊前按院内规则发起取消，并以系统返回状态为准。
                </p>
              </div>
            </div>
          </PrototypePanel>

          <PrototypePanel title="文档类型" eyebrow="Formats">
            <div className="flex flex-wrap gap-2">
              <PrototypeBadge>PDF</PrototypeBadge>
              <PrototypeBadge>DOCX</PrototypeBadge>
              <PrototypeBadge>Markdown</PrototypeBadge>
            </div>
          </PrototypePanel>
        </>
      }
    >
      <div className="grid gap-5 xl:grid-cols-[1fr_1fr]">
        <PrototypePanel title="上传文档" eyebrow="Upload">
          <div className="rounded-[1.8rem] border-2 border-dashed border-[var(--line-soft)] bg-white/78 p-10 text-center text-sm text-[var(--ink-body)]">
            拖拽或选择文档上传
          </div>
        </PrototypePanel>

        <PrototypePanel title="文档列表" eyebrow="Documents">
          <div className="space-y-3">
            {docs.map((doc) => (
              <div
                key={doc.name}
                className="rounded-[1.5rem] border border-[var(--line-soft)] bg-white/84 p-4"
              >
                <div className="flex flex-wrap items-center justify-between gap-3">
                  <div>
                    <p className="font-semibold text-[var(--ink-strong)]">{doc.name}</p>
                    <p className="mt-1 text-sm text-[var(--ink-subtle)]">最近更新时间：{doc.time}</p>
                  </div>
                  <PrototypeBadge tone={doc.status === "已发布" ? "solid" : "default"}>{doc.status}</PrototypeBadge>
                </div>
              </div>
            ))}
          </div>
        </PrototypePanel>
      </div>
    </PrototypeWorkspaceShell>
  );
}
