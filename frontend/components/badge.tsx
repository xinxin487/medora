import type { ReactNode } from "react";
import { joinClasses } from "@/lib/utils";

type BadgeProps = {
  children: ReactNode;
  tone?: "default" | "solid" | "soft" | "success" | "warning";
  className?: string;
};

export function Badge({ children, tone = "default", className }: BadgeProps) {
  const toneClass =
    tone === "solid"
      ? "border-[var(--accent-main)] bg-[var(--accent-main)] text-white"
      : tone === "soft"
        ? "border-transparent bg-[var(--accent-warm-soft)] text-[var(--accent-main-strong)]"
        : tone === "success"
          ? "border-transparent bg-[#e8f5e9] text-[#2e7d32]"
          : tone === "warning"
            ? "border-transparent bg-[#fff3e0] text-[#e65100]"
            : "border-[var(--line-soft)] bg-white/82 text-[var(--ink-body)]";

  return (
    <span
      className={joinClasses(
        "inline-flex rounded-full border px-3 py-1 text-xs font-semibold",
        toneClass,
        className,
      )}
    >
      {children}
    </span>
  );
}
