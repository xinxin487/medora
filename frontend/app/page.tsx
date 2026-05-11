import Link from "next/link";

const checkpoints = [
  "Phase 3 已完成预约创建、取消、列表和详情，号源扣减与回补可以联调验证。",
  "Phase 4 新增会话、消息落库和 SSE 基础流式返回。",
  "后续阶段会把知识库检索、Agent 工具调用和记忆能力接到这条对话链路上。",
];

export default function HomePage() {
  return (
    <main className="min-h-screen px-6 py-10 text-slate-900">
      <section className="mx-auto grid max-w-6xl gap-8 lg:grid-cols-[1.15fr_0.85fr]">
        <div className="rounded-[2rem] border border-white/60 bg-white/80 p-8 shadow-[0_25px_80px_rgba(15,23,42,0.08)] backdrop-blur">
          <span className="inline-flex rounded-full border border-emerald-200 bg-emerald-50 px-3 py-1 text-xs font-semibold uppercase tracking-[0.24em] text-emerald-700">
            Phase 4
          </span>

          <h1 className="mt-5 max-w-3xl text-4xl font-semibold leading-tight sm:text-5xl">
            Medora 已经进入对话基础能力阶段，当前可以验证会话和流式消息。
          </h1>

          <p className="mt-6 max-w-2xl text-base leading-8 text-slate-600 sm:text-lg">
            预约闭环已经完成，新的对话页会复用当前登录态创建会话、发送消息，并通过
            SSE 增量展示助手回复。后续 RAG 与 Agent 都会沿用这一条消息主链路。
          </p>

          <div className="mt-8 flex flex-wrap gap-3">
            <Link
              href="/login"
              className="rounded-full bg-slate-900 px-5 py-3 text-sm font-semibold text-white transition hover:bg-slate-700"
            >
              进入认证联调页
            </Link>
            <Link
              href="/appointments"
              className="rounded-full bg-emerald-700 px-5 py-3 text-sm font-semibold text-white transition hover:bg-emerald-600"
            >
              进入预约联调页
            </Link>
            <Link
              href="/chat"
              className="rounded-full bg-cyan-700 px-5 py-3 text-sm font-semibold text-white transition hover:bg-cyan-600"
            >
              进入对话联调页
            </Link>
            <a
              href="http://localhost:8080/api/v1/health"
              className="rounded-full border border-slate-200 bg-white px-5 py-3 text-sm font-semibold text-slate-700 transition hover:border-slate-400"
            >
              查看后端健康接口
            </a>
          </div>
        </div>

        <aside className="rounded-[2rem] border border-slate-200/80 bg-[#132926] p-7 text-white shadow-[0_25px_80px_rgba(19,41,38,0.22)]">
          <p className="text-sm uppercase tracking-[0.26em] text-emerald-200">
            Delivery Notes
          </p>
          <ul className="mt-6 space-y-4">
            {checkpoints.map((item) => (
              <li
                key={item}
                className="rounded-2xl border border-white/10 bg-white/5 px-4 py-4 text-sm leading-7 text-slate-100"
              >
                {item}
              </li>
            ))}
          </ul>
        </aside>
      </section>
    </main>
  );
}
