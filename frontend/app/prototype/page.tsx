import Link from "next/link";
import {
  PrototypeMetricCard,
  PrototypePanel,
} from "@/components/prototype-workspace-shell";

const patientPages = [
  { href: "/prototype/login", title: "统一登录", body: "账户入口、身份识别与角色分流。" },
  { href: "/prototype/patient/dashboard", title: "工作台首页", body: "服务总览、近期预约与下一步动作。" },
  { href: "/prototype/patient/appointments", title: "预约服务", body: "科室、医生、号源与确认预约。" },
  { href: "/prototype/patient/assistant", title: "导诊助手", body: "会话、引用、建议与业务衔接。" },
  { href: "/prototype/patient/services", title: "我的服务", body: "预约记录、服务进度与回看入口。" },
  { href: "/prototype/patient/profile", title: "个人档案", body: "身份资料、默认科室与就诊偏好。" },
];

const adminPages = [
  { href: "/prototype/admin/dashboard", title: "后台总览", body: "平台状态、待处理事项与快捷入口。" },
  { href: "/prototype/admin/knowledge", title: "知识库", body: "文档上传、状态管理与检索验证。" },
  { href: "/prototype/admin/conversations", title: "会话排查", body: "搜索会话、查看消息与摘要。" },
  { href: "/prototype/admin/traces", title: "Trace", body: "意图、检索、工具与模型链路。" },
  { href: "/prototype/admin/audit", title: "审计日志", body: "关键写操作、事件详情与归因。" },
];

function ScreenCard({
  href,
  title,
  body,
}: {
  href: string;
  title: string;
  body: string;
}) {
  return (
    <Link
      href={href}
      className="block rounded-[1.7rem] border border-[var(--line-soft)] bg-white/84 p-5 shadow-[0_16px_44px_rgba(20,42,39,0.05)] transition hover:-translate-y-0.5 hover:border-[var(--accent-main)] hover:bg-white"
    >
      <div className="flex items-center justify-between gap-3">
        <h3 className="text-lg font-semibold text-[var(--ink-strong)]">{title}</h3>
        <span className="text-sm font-semibold text-[var(--accent-main)]">进入</span>
      </div>
      <p className="mt-3 text-sm leading-6 text-[var(--ink-body)]">{body}</p>
    </Link>
  );
}

export default function PrototypeIndexPage() {
  return (
    <main className="min-h-screen px-4 py-6 sm:px-6">
      <div className="mx-auto max-w-7xl space-y-6">
        <section className="medora-surface medora-grid border border-white/60 p-6 sm:p-8">
          <div className="flex flex-wrap items-start justify-between gap-4">
            <div className="max-w-4xl">
              <div className="text-xs font-semibold uppercase tracking-[0.24em] text-[var(--accent-main)]">
                Medora Screen Library
              </div>
              <h1 className="medora-display mt-3 text-4xl leading-tight text-[var(--ink-strong)] sm:text-[3.1rem]">
                Medora 网页端界面总览
              </h1>
              <p className="mt-4 text-sm leading-8 text-[var(--ink-body)] sm:text-base">
                统一登录、患者服务台与管理控制台，按正式网页端布局整理为一组完整界面。
              </p>
            </div>
            <Link
              href="/prototype/login"
              className="rounded-full bg-[var(--accent-main)] px-5 py-3 text-sm font-semibold text-white shadow-[0_18px_40px_rgba(31,92,82,0.24)] transition hover:bg-[var(--accent-main-strong)]"
            >
              打开登录页
            </Link>
          </div>

          <div className="mt-8 grid gap-4 md:grid-cols-3">
            <PrototypeMetricCard label="患者界面" value="5 页" hint="工作台首页、预约、导诊、服务、档案。" />
            <PrototypeMetricCard label="管理界面" value="5 页" hint="总览、知识库、会话、Trace、审计。" />
            <PrototypeMetricCard label="入口界面" value="1 页" hint="统一登录后分流到对应工作区。" />
          </div>
        </section>

        <div className="grid gap-6 xl:grid-cols-2">
          <PrototypePanel title="患者服务台" eyebrow="Patient">
            <div className="grid gap-4 md:grid-cols-2">
              {patientPages.map((page) => (
                <ScreenCard key={page.href} {...page} />
              ))}
            </div>
          </PrototypePanel>

          <PrototypePanel title="管理控制台" eyebrow="Admin">
            <div className="grid gap-4 md:grid-cols-2">
              {adminPages.map((page) => (
                <ScreenCard key={page.href} {...page} />
              ))}
            </div>
          </PrototypePanel>
        </div>
      </div>
    </main>
  );
}
