export default function HomePage() {
  return (
    <main className="min-h-screen bg-brand-mist text-brand-ink">
      <section className="mx-auto flex min-h-screen max-w-5xl flex-col justify-center px-6 py-16">
        <span className="mb-4 inline-flex w-fit rounded-full border border-brand-line bg-white px-3 py-1 text-xs font-semibold tracking-[0.2em] text-brand-teal">
          PHASE 0
        </span>

        <h1 className="max-w-3xl text-4xl font-bold leading-tight sm:text-5xl">
          Medora 工程骨架已初始化
        </h1>

        <p className="mt-6 max-w-2xl text-base leading-8 text-slate-700 sm:text-lg">
          当前阶段完成了前后端最小骨架、基础目录结构、环境模板和启动说明。
          下一步将按实施计划进入数据库与主数据阶段，逐步落地医疗服务业务闭环和智能助手能力。
        </p>

        <div className="mt-10 grid gap-4 sm:grid-cols-2">
          <div className="rounded-2xl border border-brand-line bg-white p-5">
            <h2 className="text-lg font-semibold">后端骨架</h2>
            <p className="mt-2 text-sm leading-7 text-slate-600">
              Spring Boot 最小工程已准备好，包含基础健康检查接口与统一响应结构占位。
            </p>
          </div>

          <div className="rounded-2xl border border-brand-line bg-white p-5">
            <h2 className="text-lg font-semibold">前端骨架</h2>
            <p className="mt-2 text-sm leading-7 text-slate-600">
              Next.js App Router 与 Tailwind 基础结构已就绪，后续将逐步接入登录、对话和预约页面。
            </p>
          </div>
        </div>
      </section>
    </main>
  );
}
