type MetricCardProps = {
  label: string;
  value: string;
  hint: string;
  trend?: "up" | "down" | "neutral";
  trendLabel?: string;
};

export function MetricCard({
  label,
  value,
  hint,
  trend,
  trendLabel,
}: MetricCardProps) {
  return (
    <div className="rounded-[1.6rem] border border-[var(--line-soft)] bg-white/84 p-4 shadow-[0_18px_40px_rgba(20,42,39,0.05)]">
      <div className="text-xs font-semibold uppercase tracking-[0.18em] text-[var(--ink-subtle)]">
        {label}
      </div>
      <div className="mt-3 flex items-baseline gap-2">
        <span className="text-3xl font-semibold text-[var(--ink-strong)]">
          {value}
        </span>
        {trend && trendLabel ? (
          <span
            className={`text-xs font-semibold ${
              trend === "up"
                ? "text-[#2e7d32]"
                : trend === "down"
                  ? "text-[#c62828]"
                  : "text-[var(--ink-subtle)]"
            }`}
          >
            {trend === "up" ? "↑" : trend === "down" ? "↓" : "→"}{" "}
            {trendLabel}
          </span>
        ) : null}
      </div>
      <p className="mt-2 text-sm leading-6 text-[var(--ink-body)]">{hint}</p>
    </div>
  );
}
