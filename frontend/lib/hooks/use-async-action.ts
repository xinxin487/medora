"use client";

import { useState } from "react";
import { ApiClientError } from "@/lib/api/client";

export function useAsyncAction() {
  const [isBusy, setIsBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function runAction(action: () => Promise<void>, resetError = true) {
    setIsBusy(true);
    if (resetError) setError(null);
    try {
      await action();
    } catch (cause) {
      if (cause instanceof ApiClientError) {
        const detailText =
          cause.details.length > 0
            ? ` ${cause.details.map((d) => `${d.field}: ${d.reason}`).join(" | ")}`
            : "";
        setError(`${cause.code}: ${cause.message}${detailText}`);
      } else {
        setError("请求失败，请确认已登录并且后端服务已启动。");
      }
    } finally {
      setIsBusy(false);
    }
  }

  return { isBusy, error, setError, runAction };
}
