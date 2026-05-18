import Link from "next/link";
import { PatientShell } from "@/components/patient-shell";

const serviceHighlights = [
  {
    title: "预约中心",
    body: "从科室、医生到号源的核心路径被组织进同一页里，方便用户快速完成真实预约动作。",
  },
  {
    title: "导诊助手",
    body: "导诊不是孤立聊天，而是能结合知识检索、工具调用和上下文记忆的会话式服务。",
  },
  {
    title: "连续服务",
    body: "上一轮对话、偏好和成功预约记录可以被持续利用，让体验更像持续跟进而不是一次性问答。",
  },
];

const trustSignals = [
  "真实预约与取消接口已经接通，不是静态样例。",
  "导诊助手支持 SSE 流式回复、引用片段和工具状态反馈。",
  "后台可查看会话摘要、Agent Trace、审计日志和 Prometheus 指标。",
];

export default function HomePage() {
  return (
    <PatientShell>
      <section className="grid gap-5 lg:grid-cols-[1.15fr_0.85fr]">
        <div className="medora-surface medora-grid border border-[var(--line-soft)] px-6 py-7 sm:px-8 sm:py-9">
          <p className="text-xs font-semibold uppercase tracking-[0.3em] text-[var(--accent-main)]">
            Care Navigation, Reimagined
          </p>
          <h1 className="medora-display mt-5 max-w-4xl text-[3.3rem] font-semibold leading-[0.96] tracking-[-0.06em] text-[var(--ink-strong)] sm:text-[4.8rem]">
            把挂号、问诊准备和连续导诊，组织成一条真正顺手的用户路径。
          </h1>
          <p className="mt-6 max-w-3xl text-base leading-8 text-[var(--ink-body)] sm:text-lg">
            Medora 不只是一个聊天窗口，也不只是一个预约入口。它把科室选择、号源预约、
            医疗知识检索和上下文连续性整合在一起，让用户从“不知道挂什么科”走到“已经完成预约”。
          </p>

          <div className="mt-8 flex flex-wrap gap-3">
            <Link
              href="/appointments"
              className="rounded-full bg-[var(--ink-strong)] px-6 py-3 text-sm font-semibold text-white shadow-[0_18px_36px_rgba(20,42,39,0.18)] transition hover:translate-y-[-1px]"
            >
              进入预约中心
            </Link>
            <Link
              href="/chat"
              className="rounded-full border border-[var(--accent-main)] bg-[rgba(31,92,82,0.08)] px-6 py-3 text-sm font-semibold text-[var(--accent-main)] transition hover:border-[var(--accent-main-strong)]"
            >
              开始导诊助手
            </Link>
            <Link
              href="/login"
              className="rounded-full border border-[var(--line-soft)] bg-white px-6 py-3 text-sm font-semibold text-[var(--ink-body)] transition hover:border-[var(--accent-warm)]"
            >
              登录或完善账户
            </Link>
          </div>

          <div className="mt-9 grid gap-4 md:grid-cols-3">
            {serviceHighlights.map((item) => (
              <article
                key={item.title}
                className="rounded-[1.6rem] border border-[rgba(20,42,39,0.08)] bg-[rgba(255,255,255,0.68)] px-4 py-5"
              >
                <h2 className="text-base font-semibold text-[var(--ink-strong)]">{item.title}</h2>
                <p className="mt-3 text-sm leading-7 text-[var(--ink-body)]">{item.body}</p>
              </article>
            ))}
          </div>
        </div>

        <aside className="grid gap-5">
          <div className="medora-surface border border-[var(--line-soft)] bg-[linear-gradient(180deg,rgba(237,243,238,0.92),rgba(255,255,255,0.9))] px-6 py-7">
            <p className="text-xs font-semibold uppercase tracking-[0.24em] text-[var(--ink-subtle)]">
              Primary Paths
            </p>
            <div className="mt-5 space-y-4">
              <div className="rounded-[1.4rem] border border-[rgba(31,92,82,0.14)] bg-white/70 px-4 py-4">
                <p className="text-sm font-semibold text-[var(--accent-main)]">01. 找合适的科室和医生</p>
                <p className="mt-2 text-sm leading-7 text-[var(--ink-body)]">
                  从预约中心进入，先缩小科室范围，再查看对应医生和可预约时段。
                </p>
              </div>
              <div className="rounded-[1.4rem] border border-[rgba(31,92,82,0.14)] bg-white/70 px-4 py-4">
                <p className="text-sm font-semibold text-[var(--accent-main)]">02. 在导诊中继续追问</p>
                <p className="mt-2 text-sm leading-7 text-[var(--ink-body)]">
                  如果不确定挂号方向，可以转到导诊助手继续问，系统会保留对话和上下文。
                </p>
              </div>
              <div className="rounded-[1.4rem] border border-[rgba(31,92,82,0.14)] bg-white/70 px-4 py-4">
                <p className="text-sm font-semibold text-[var(--accent-main)]">03. 完成预约并回看状态</p>
                <p className="mt-2 text-sm leading-7 text-[var(--ink-body)]">
                  创建预约后，可以在同一页面回看我的预约，并执行取消等真实业务动作。
                </p>
              </div>
            </div>
          </div>

          <div className="medora-surface border border-[var(--line-soft)] px-6 py-7">
            <p className="text-xs font-semibold uppercase tracking-[0.24em] text-[var(--accent-warm)]">
              Trust Signals
            </p>
            <ul className="mt-5 space-y-4">
              {trustSignals.map((item) => (
                <li
                  key={item}
                  className="rounded-[1.3rem] border border-[rgba(184,132,81,0.16)] bg-[rgba(184,132,81,0.06)] px-4 py-4 text-sm leading-7 text-[var(--ink-body)]"
                >
                  {item}
                </li>
              ))}
            </ul>
          </div>
        </aside>
      </section>

      <section className="mt-6 grid gap-5 lg:grid-cols-[0.92fr_1.08fr]">
        <div className="medora-surface border border-[var(--line-soft)] px-6 py-7">
          <p className="text-xs font-semibold uppercase tracking-[0.24em] text-[var(--ink-subtle)]">
            Why This Matters
          </p>
          <h2 className="medora-display mt-4 text-3xl font-semibold tracking-[-0.04em]">
            一个真正有用的医疗服务前端，不应该让用户在“问”和“做”之间来回跳。
          </h2>
          <p className="mt-5 text-base leading-8 text-[var(--ink-body)]">
            Medora 的正式用户端会把咨询、筛选、预约、回看会话这些动作串在一起。
            用户看到的不是 Phase 文案、接口按钮和调试状态，而是一条完整的服务旅程。
          </p>
        </div>

        <div className="grid gap-5 md:grid-cols-2">
          <article className="medora-surface border border-[var(--line-soft)] px-5 py-6">
            <p className="text-xs font-semibold uppercase tracking-[0.24em] text-[var(--ink-subtle)]">
              预约路径
            </p>
            <h3 className="mt-3 text-xl font-semibold">预约中心</h3>
            <p className="mt-3 text-sm leading-7 text-[var(--ink-body)]">
              通过日期、科室、医生和时段筛选，把真实可预约号源组织成可决策的界面。
            </p>
            <Link
              href="/appointments"
              className="mt-5 inline-flex text-sm font-semibold text-[var(--accent-main)]"
            >
              去看正式预约页
            </Link>
          </article>

          <article className="medora-surface border border-[var(--line-soft)] px-5 py-6">
            <p className="text-xs font-semibold uppercase tracking-[0.24em] text-[var(--ink-subtle)]">
              导诊路径
            </p>
            <h3 className="mt-3 text-xl font-semibold">会话式医疗助手</h3>
            <p className="mt-3 text-sm leading-7 text-[var(--ink-body)]">
              用带引用、带工具状态和带上下文连续性的对话方式，帮助用户迈向下一步动作。
            </p>
            <Link href="/chat" className="mt-5 inline-flex text-sm font-semibold text-[var(--accent-main)]">
              去看正式导诊页
            </Link>
          </article>
        </div>
      </section>
    </PatientShell>
  );
}
