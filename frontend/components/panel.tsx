import type { ReactNode } from "react";
import { joinClasses } from "@/lib/utils";

type PanelProps = {
  title: string;
  eyebrow?: string;
  description?: string;
  className?: string;
  actions?: ReactNode;
  children: ReactNode;
};

export function Panel({
  title,
  eyebrow,
  description,
  className,
  actions,
  children,
}: PanelProps) {
  return (
    <section
      className={joinClasses(
        "medora-surface medora-grid border border-white/60 p-5 sm:p-6",
        className,
      )}
    >
      {eyebrow ? (
        <div className="text-xs font-semibold uppercase tracking-[0.22em] text-[var(--accent-main)]">
          {eyebrow}
        </div>
      ) : null}
      <div className="mt-2 flex flex-wrap items-end justify-between gap-3">
        <h3 className="text-xl font-semibold text-[var(--ink-strong)]">
          {title}
        </h3>
        {actions ? (
          <div className="flex flex-wrap items-center gap-2">{actions}</div>
        ) : null}
      </div>
      {description ? (
        <p className="mt-2 text-sm leading-7 text-[var(--ink-body)]">
          {description}
        </p>
      ) : null}
      <div className="mt-4">{children}</div>
    </section>
  );
}
