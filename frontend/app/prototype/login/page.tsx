import Link from "next/link";
import { PrototypeBadge } from "@/components/prototype-workspace-shell";

const highlights = [
  { title: "连续导诊", body: "保留近期会话、引用与就诊方向。" },
  { title: "快速预约", body: "从科室筛选到锁定号源一页完成。" },
  { title: "统一后台", body: "知识库、Trace 与审计集中管理。" },
];

export default function PrototypeLoginPage() {
  return (
    <main className="min-h-screen px-4 py-6 sm:px-6">
      <div className="mx-auto max-w-7xl">
        <div className="grid gap-6 xl:grid-cols-[1.08fr_0.92fr]">
          <section className="overflow-hidden rounded-[2.4rem] bg-[linear-gradient(135deg,#143b35_0%,#1f5c52_52%,#2c776c_100%)] p-7 text-white shadow-[0_30px_90px_rgba(20,42,39,0.24)] sm:p-10">
            <div className="flex items-center justify-between gap-3">
              <div>
                <div className="text-xs font-semibold uppercase tracking-[0.28em] text-white/60">Medora</div>
                <div className="mt-2 text-sm font-semibold text-white/82">Medical Access Platform</div>
              </div>
              <span className="rounded-full border border-white/14 bg-white/10 px-4 py-2 text-sm font-semibold text-white/80">
                安全入口
              </span>
            </div>

            <div className="mt-14 max-w-3xl">
              <h1 className="medora-display text-5xl leading-[1.06] text-white sm:text-[4.5rem]">
                把导诊、挂号与连续服务放进同一个入口。
              </h1>
              <p className="mt-6 max-w-2xl text-base leading-8 text-white/76">
                登录后自动进入对应工作区，继续你的预约、导诊和后台运营任务。
              </p>
            </div>

            <div className="mt-10 grid gap-4 md:grid-cols-3">
              {highlights.map((item) => (
                <div key={item.title} className="rounded-[1.7rem] border border-white/12 bg-white/8 p-5 backdrop-blur">
                  <p className="text-lg font-semibold">{item.title}</p>
                  <p className="mt-3 text-sm leading-6 text-white/70">{item.body}</p>
                </div>
              ))}
            </div>
          </section>

          <section className="medora-surface medora-grid border border-white/60 p-6 sm:p-8">
            <div className="flex items-center justify-between gap-3">
              <div>
                <div className="text-xs font-semibold uppercase tracking-[0.24em] text-[var(--accent-main)]">
                  Unified Sign In
                </div>
                <h2 className="mt-3 text-3xl font-semibold text-[var(--ink-strong)]">欢迎回来</h2>
              </div>
              <PrototypeBadge tone="soft">自动识别角色</PrototypeBadge>
            </div>

            <div className="mt-8 space-y-4">
              <div className="rounded-[1.35rem] border border-[var(--line-soft)] bg-[var(--bg-soft)]/70 px-4 py-3.5 text-sm text-[var(--ink-subtle)]">
                手机号 / 邮箱
              </div>
              <div className="rounded-[1.35rem] border border-[var(--line-soft)] bg-[var(--bg-soft)]/70 px-4 py-3.5 text-sm text-[var(--ink-subtle)]">
                密码
              </div>
            </div>

            <div className="mt-4 flex flex-wrap items-center justify-between gap-3">
              <div className="flex flex-wrap gap-2">
                <PrototypeBadge>记住本设备</PrototypeBadge>
                <PrototypeBadge>安全登录</PrototypeBadge>
              </div>
              <span className="text-sm font-semibold text-[var(--accent-main)]">忘记密码</span>
            </div>

            <div className="mt-6 space-y-3">
              <Link
                href="/prototype/patient/dashboard"
                className="block rounded-[1.35rem] bg-[var(--accent-main)] px-4 py-3.5 text-center text-sm font-semibold text-white shadow-[0_18px_44px_rgba(31,92,82,0.22)] transition hover:bg-[var(--accent-main-strong)]"
              >
                登录并进入患者服务台
              </Link>
              <Link
                href="/prototype/admin/dashboard"
                className="block rounded-[1.35rem] border border-[var(--line-soft)] bg-white px-4 py-3.5 text-center text-sm font-semibold text-[var(--ink-strong)] transition hover:border-[var(--accent-main)] hover:text-[var(--accent-main)]"
              >
                登录并进入管理控制台
              </Link>
            </div>

            <div className="mt-8 grid gap-3 md:grid-cols-2">
              <div className="rounded-[1.5rem] border border-[var(--line-soft)] bg-white/82 p-4">
                <p className="text-sm font-semibold text-[var(--ink-strong)]">患者入口</p>
                <p className="mt-2 text-sm leading-6 text-[var(--ink-body)]">
                  预约服务、导诊助手、我的服务、个人档案
                </p>
              </div>
              <div className="rounded-[1.5rem] border border-[var(--line-soft)] bg-white/82 p-4">
                <p className="text-sm font-semibold text-[var(--ink-strong)]">管理员入口</p>
                <p className="mt-2 text-sm leading-6 text-[var(--ink-body)]">
                  知识库、会话排查、Trace、审计日志
                </p>
              </div>
            </div>
          </section>
        </div>
      </div>
    </main>
  );
}
