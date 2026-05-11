import Link from "next/link";

const checkpoints = [
  "后端已经补齐 JWT、Refresh Token、统一安全拦截和当前用户接口。",
  "前端新增了 Phase 2 认证联调页，可直接验证注册、登录和资料更新。",
  "下一阶段会沿着实施计划进入预约业务闭环，也就是 Phase 3。",
];

export default function HomePage() {
  return (
    <main className="min-h-screen px-6 py-10 text-slate-900">
      <section className="mx-auto grid max-w-6xl gap-8 lg:grid-cols-[1.15fr_0.85fr]">
        <div className="rounded-[2rem] border border-white/60 bg-white/80 p-8 shadow-[0_25px_80px_rgba(15,23,42,0.08)] backdrop-blur">
          <span className="inline-flex rounded-full border border-emerald-200 bg-emerald-50 px-3 py-1 text-xs font-semibold uppercase tracking-[0.24em] text-emerald-700">
            Phase 2
          </span>

          <h1 className="mt-5 max-w-3xl text-4xl font-semibold leading-tight sm:text-5xl">
            Medora 已经接入用户认证主链路，当前可以直接进入登录联调。
          </h1>

          <p className="mt-6 max-w-2xl text-base leading-8 text-slate-600 sm:text-lg">
            这一步把用户身份能力从设计文档落实到了代码，包括登录、注册、JWT
            鉴权、Refresh Token 和当前用户资料读取。后续预约、会话和后台模块都将复用这套基础能力。
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
