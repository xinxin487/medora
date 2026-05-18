import Link from "next/link";
import {
  PrototypeMetricCard,
  PrototypePanel,
  PrototypeWorkspaceShell,
} from "@/components/prototype-workspace-shell";

const quickLinks = [
  { href: "/prototype/admin/knowledge", title: "处理待重建文档", body: "2 份文档需重建或重新发布。" },
  { href: "/prototype/admin/conversations", title: "查看异常会话", body: "最近 1 条会话需要回看消息与摘要。" },
  { href: "/prototype/admin/traces", title: "检查工具链路", body: "追踪号源查询与预约工具执行情况。" },
  { href: "/prototype/admin/audit", title: "回看审计事件", body: "最近 24 小时有 3 条重要写操作。" },
];

export default function PrototypeAdminDashboardPage() {
  return (
    <PrototypeWorkspaceShell
      role="admin"
      activeKey="dashboard"
      title="管理控制台"
      subtitle="平台状态、待处理事项与关键后台入口。"
      actions={
        <>
          <Link
            href="/prototype/admin/knowledge"
            className="rounded-full bg-[var(--accent-main)] px-5 py-3 text-sm font-semibold text-white transition hover:bg-[var(--accent-main-strong)]"
          >
            知识库
          </Link>
          <Link
            href="/prototype/admin/conversations"
            className="rounded-full border border-[var(--line-soft)] bg-white px-5 py-3 text-sm font-semibold text-[var(--ink-strong)] transition hover:border-[var(--accent-main)] hover:text-[var(--accent-main)]"
          >
            会话排查
          </Link>
        </>
      }
      aside={
        <>
          <PrototypePanel title="系统状态" eyebrow="Health">
            <div className="space-y-3 text-sm text-[var(--ink-body)]">
              <div className="rounded-2xl border border-[var(--line-soft)] bg-white/82 px-4 py-3">API：正常</div>
              <div className="rounded-2xl border border-[var(--line-soft)] bg-white/82 px-4 py-3">知识任务：1 个进行中</div>
              <div className="rounded-2xl border border-[var(--line-soft)] bg-white/82 px-4 py-3">最近异常：1 条需回看</div>
            </div>
          </PrototypePanel>

          <PrototypePanel title="最近事件" eyebrow="Recent">
            <div className="space-y-3 text-sm text-[var(--ink-body)]">
              <div className="rounded-2xl border border-[var(--line-soft)] bg-white/82 px-4 py-3">10:42 · 用户预约成功</div>
              <div className="rounded-2xl border border-[var(--line-soft)] bg-white/82 px-4 py-3">10:20 · 文档发布完成</div>
            </div>
          </PrototypePanel>
        </>
      }
    >
      <div className="grid gap-5 md:grid-cols-4">
        <PrototypeMetricCard label="24h 会话" value="148" hint="最近 24 小时累计会话量。" />
        <PrototypeMetricCard label="知识文档" value="32" hint="已发布与待处理文档总量。" />
        <PrototypeMetricCard label="工具成功率" value="96.4%" hint="预约、取消和号源工具整体表现。" />
        <PrototypeMetricCard label="审计提醒" value="3" hint="待回看的关键写操作数量。" />
      </div>

      <PrototypePanel title="快捷入口" eyebrow="Quick Access">
        <div className="grid gap-4 md:grid-cols-2">
          {quickLinks.map((item) => (
            <Link
              key={item.href}
              href={item.href}
              className="block rounded-[1.6rem] border border-[var(--line-soft)] bg-white/84 p-5 transition hover:border-[var(--accent-main)] hover:bg-white"
            >
              <div className="flex items-center justify-between gap-3">
                <h3 className="text-lg font-semibold text-[var(--ink-strong)]">{item.title}</h3>
                <span className="text-sm font-semibold text-[var(--accent-main)]">进入</span>
              </div>
              <p className="mt-3 text-sm leading-6 text-[var(--ink-body)]">{item.body}</p>
            </Link>
          ))}
        </div>
      </PrototypePanel>
    </PrototypeWorkspaceShell>
  );
}
