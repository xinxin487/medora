"use client";

import { useEffect, useState } from "react";
import { apiFetch } from "@/lib/api/client";
import { useAuth } from "@/lib/hooks/use-auth";
import { WorkspaceShell } from "@/components/workspace-shell";
import { Panel } from "@/components/panel";
import { MetricCard } from "@/components/metric-card";
import { Badge } from "@/components/badge";

type SafetyStats = {
  dailySafetyBlocks: Record<string, number>;
  hallucinationRiskDistribution: Record<string, number>;
  totalRecentTraces: number;
  highRiskTraceIds: string[];
  toolCallSuccessRate: number;
  totalToolCalls: number;
};

function safeValue(value: unknown): string {
  if (value === null || value === undefined) return "-";
  if (typeof value === "number") return value.toLocaleString();
  return String(value);
}

export default function AdminSafetyPage() {
  const { isLoading: authLoading } = useAuth("ADMIN");

  const [stats, setStats] = useState<SafetyStats | null>(null);
  const [status, setStatus] = useState("正在加载安全概览。");
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!authLoading) {
      void loadSafetyStats();
    }
  }, [authLoading]);

  async function loadSafetyStats() {
    setStatus("正在加载安全概览。");
    setError(null);
    try {
      const response: Response = await apiFetch("/api/v1/admin/safety-stats");
      const body: { code: string; message?: string; data: SafetyStats } =
        await response.json();
      if (body.code === "SUCCESS") {
        setStats(body.data);
        setStatus("已加载安全概览。");
      } else {
        setError(body.message || "加载安全概览失败。");
        setStatus("error");
      }
    } catch {
      setError("无法连接后端，请确认后端服务已启动。");
      setStatus("error");
    }
  }

  if (authLoading) {
    return (
      <WorkspaceShell role="admin" activeKey="" title="安全概览">
        <div className="flex items-center justify-center min-h-[60vh] text-slate-400">
          验证身份...
        </div>
      </WorkspaceShell>
    );
  }

  const totalBlocks = stats
    ? Object.values(stats.dailySafetyBlocks).reduce((a, b) => a + b, 0)
    : 0;

  return (
    <WorkspaceShell role="admin" activeKey="" title="安全概览">
      <div className="p-6 space-y-6">
        {/* 页面标题 */}
        <div className="flex items-center justify-between">
          <div>
            <h1 className="text-xl font-semibold text-slate-800">安全概览</h1>
            <p className="text-sm text-slate-500 mt-1">
              安全拦截统计、幻觉风险评估与工具调用监控
            </p>
          </div>
          <Badge tone={error ? "warning" : "success"}>
            {error ? "加载失败" : "运行中"}
          </Badge>
        </div>

        {error && (
          <Panel title="加载错误" eyebrow="">
            <p className="text-red-500 text-sm">{error}</p>
          </Panel>
        )}

        {/* 指标卡片 */}
        <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
          <MetricCard
            label="近7天 Trace 总量"
            value={safeValue(stats?.totalRecentTraces)}
            hint={
              stats
                ? `${stats.hallucinationRiskDistribution.HIGH ?? 0} 个高风险`
                : "加载中"
            }
            trend={
              stats &&
              (stats.hallucinationRiskDistribution.HIGH ?? 0) >
                (stats.hallucinationRiskDistribution.LOW ?? 0)
                ? "down"
                : "neutral"
            }
            trendLabel="高风险占比"
          />
          <MetricCard
            label="工具调用成功率"
            value={
              stats
                ? `${(stats.toolCallSuccessRate * 100).toFixed(0)}%`
                : "-"
            }
            hint={
              stats && stats.totalToolCalls > 0
                ? `${stats.totalToolCalls} 次总调用`
                : "暂无数据"
            }
            trend={
              stats && stats.toolCallSuccessRate >= 0.9
                ? "up"
                : "neutral"
            }
            trendLabel="成功率"
          />
          <MetricCard
            label="安全拦截次数"
            value={safeValue(totalBlocks)}
            hint={
              stats && Object.keys(stats.dailySafetyBlocks).length > 0
                ? `最近活动: ${Object.keys(stats.dailySafetyBlocks).join(", ")}`
                : "无拦截事件"
            }
            trend={totalBlocks > 0 ? "down" : "neutral"}
            trendLabel="拦截数"
          />
        </div>

        {/* 幻觉风险分布 */}
        <Panel
          title="幻觉风险分布"
          eyebrow="RISK"
          description="Agent Trace 中的幻觉风险评估"
          actions={
            <select className="text-xs border rounded px-2 py-1 text-slate-600">
              <option>近7天</option>
            </select>
          }
        >
          {stats ? (
            <div className="space-y-3">
              <RiskBar
                label="低风险"
                count={
                  stats.hallucinationRiskDistribution.LOW ?? 0
                }
                total={stats.totalRecentTraces}
                color="bg-green-400"
              />
              <RiskBar
                label="中风险"
                count={
                  stats.hallucinationRiskDistribution.MEDIUM ?? 0
                }
                total={stats.totalRecentTraces}
                color="bg-yellow-400"
              />
              <RiskBar
                label="高风险"
                count={
                  stats.hallucinationRiskDistribution.HIGH ?? 0
                }
                total={stats.totalRecentTraces}
                color="bg-red-400"
              />
            </div>
          ) : (
            <p className="text-slate-400 text-sm">加载中...</p>
          )}
        </Panel>

        {/* 高风险 Trace */}
        <Panel
          title="高风险 Trace"
          eyebrow="TRACE"
          description="hallucination_risk=HIGH 的最近记录"
        >
          {stats && stats.highRiskTraceIds.length > 0 ? (
            <ul className="space-y-2">
              {stats.highRiskTraceIds.map((traceId) => (
                <li key={traceId}>
                  <a
                    href={`/admin/traces?traceId=${traceId}`}
                    className="text-sm text-teal-600 hover:text-teal-700 underline underline-offset-2"
                  >
                    {traceId}
                  </a>
                </li>
              ))}
            </ul>
          ) : (
            <p className="text-slate-400 text-sm">
              暂无高风险记录。
            </p>
          )}
        </Panel>

        {/* 按日安全拦截 */}
        <Panel
          title="按日安全拦截"
          eyebrow="BLOCK"
          description="每天被 SafetyGuard 拦截的请求数"
        >
          {stats && Object.keys(stats.dailySafetyBlocks).length > 0 ? (
            <div className="space-y-2">
              {Object.entries(stats.dailySafetyBlocks).map(
                ([day, count]) => (
                  <div
                    key={day}
                    className="flex items-center gap-3 text-sm"
                  >
                    <span className="w-24 text-slate-600">{day}</span>
                    <div className="flex-1 bg-slate-100 rounded-full h-3">
                      <div
                        className="bg-red-400 h-3 rounded-full"
                        style={{
                          width: `${Math.min(
                            100,
                            (count / Math.max(...Object.values(stats.dailySafetyBlocks))) * 100
                          )}%`,
                        }}
                      />
                    </div>
                    <span className="w-8 text-right text-slate-500">
                      {count}
                    </span>
                  </div>
                )
              )}
            </div>
          ) : (
            <p className="text-slate-400 text-sm">
              暂无拦截记录。
            </p>
          )}
        </Panel>
      </div>
    </WorkspaceShell>
  );
}

function RiskBar({
  label,
  count,
  total,
  color,
}: {
  label: string;
  count: number;
  total: number;
  color: string;
}) {
  const pct = total > 0 ? (count / total) * 100 : 0;
  return (
    <div className="flex items-center gap-3 text-sm">
      <span className="w-12 text-slate-600">{label}</span>
      <div className="flex-1 bg-slate-100 rounded-full h-4">
        <div
          className={`${color} h-4 rounded-full transition-all`}
          style={{ width: `${Math.min(100, pct)}%` }}
        />
      </div>
      <span className="w-16 text-right text-slate-500">
        {count} ({pct.toFixed(0)}%)
      </span>
    </div>
  );
}
