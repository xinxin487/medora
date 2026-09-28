"use client";

import { useEffect, useMemo, useState } from "react";
import { submitA2uiAction, type A2uiActionResult } from "@/lib/a2ui/action-client";
import type { A2uiSurface } from "@/lib/a2ui/types";

export function A2uiSurfaceCard({ surface, onResolved }: { surface: A2uiSurface; onResolved: (surfaceId: string, result: A2uiActionResult) => void }) {
  const [submitting, setSubmitting] = useState(false);
  const [message, setMessage] = useState("");
  const deadline = useMemo(() => new Date(surface.dataModel.expiresAt).getTime(), [surface.dataModel.expiresAt]);
  const [remaining, setRemaining] = useState(() => Math.max(0, deadline - Date.now()));
  useEffect(() => {
    const timer = window.setInterval(() => setRemaining(Math.max(0, deadline - Date.now())), 1000);
    return () => window.clearInterval(timer);
  }, [deadline]);
  const confirm = async () => {
    if (submitting || remaining <= 0) return;
    setSubmitting(true);
    try {
      const result = await submitA2uiAction({ surfaceId: surface.surfaceId, actionId: surface.dataModel.actionId,
        confirmationToken: surface.dataModel.confirmationToken, idempotencyKey: surface.dataModel.idempotencyKey });
      setMessage("预约已确认。"); onResolved(surface.surfaceId, result);
    } catch (error) { setMessage(error instanceof Error ? error.message : "确认失败，请重试。"); }
    finally { setSubmitting(false); }
  };
  const label = remaining <= 0 ? "确认已过期" : `请在 ${Math.ceil(remaining / 1000)} 秒内确认`;
  return <section className="rounded-[1.4rem] border border-[var(--accent-main)] bg-white p-4 text-sm text-[var(--ink-strong)]">
    <div className="flex items-center justify-between"><strong>确认预约</strong><span className="text-xs text-[var(--ink-subtle)]">{label}</span></div>
    <p className="mt-2 text-[var(--ink-body)]">挂号费：{surface.dataModel.fee || "待确认"} 元</p>
    <button type="button" disabled={submitting || remaining <= 0} onClick={() => void confirm()}
      className="mt-3 rounded-full bg-[var(--accent-main)] px-4 py-2 text-xs font-semibold text-white disabled:opacity-50">
      {submitting ? "确认中…" : "确认预约"}
    </button>
    {message ? <p className="mt-2 text-xs text-[var(--ink-body)]">{message}</p> : null}
  </section>;
}
