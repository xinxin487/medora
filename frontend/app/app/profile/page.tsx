"use client";

import { useEffect, useState } from "react";
import type { FormEvent } from "react";
import { apiFetch } from "@/lib/api/client";
import { useAuth } from "@/lib/hooks/use-auth";
import { useAsyncAction } from "@/lib/hooks/use-async-action";
import type { PageResponse } from "@/lib/types";
import type { CurrentUser, Department } from "@/lib/types/api";
import { WorkspaceShell } from "@/components/workspace-shell";
import { Panel } from "@/components/panel";
import { Badge } from "@/components/badge";

export default function ProfilePage() {
  const { user, isLoading: authLoading } = useAuth("USER");
  const { isBusy, error, runAction } = useAsyncAction();

  const [profileForm, setProfileForm] = useState({
    displayName: "",
    defaultHospitalCode: "MAIN",
    defaultDepartmentId: "",
  });
  const [departments, setDepartments] = useState<Department[]>([]);
  const [message, setMessage] = useState<string | null>(null);

  useEffect(() => {
    if (!user) return;
    setProfileForm({
      displayName: user.displayName,
      defaultHospitalCode: user.defaultHospitalCode ?? "MAIN",
      defaultDepartmentId: user.defaultDepartment?.departmentId ?? "",
    });
    void loadDepartments();
  }, [user]);

  async function loadDepartments() {
    await runAction(async () => {
      const result = await apiFetch<PageResponse<Department>>(
        "/departments?status=ACTIVE&pageSize=50",
      );
      setDepartments(result.list);
    }, false);
  }

  async function handleProfileUpdate(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    await runAction(async () => {
      const result = await apiFetch<CurrentUser>("/users/me", {
        method: "PUT",
        body: JSON.stringify(profileForm),
      });
      setMessage(`个人资料已更新：${result.displayName}`);
    });
  }

  if (authLoading) return <LoadingSkeleton />;

  return (
    <WorkspaceShell
      role="patient"
      activeKey="profile"
      title="个人档案"
      subtitle="身份资料、默认就诊方向与服务偏好。"
      aside={
        <>
          <Panel title="账户状态" eyebrow="Account">
            <div className="space-y-3 text-sm text-[var(--ink-body)]">
              <div className="rounded-2xl border border-[var(--line-soft)] bg-white/82 px-4 py-3">
                <span className="text-xs font-semibold uppercase tracking-[0.18em] text-[var(--ink-subtle)]">
                  当前角色
                </span>
                <p className="mt-2 font-semibold text-[var(--ink-strong)]">
                  {user?.roles.join(", ") ?? "USER"}
                </p>
              </div>
              <div className="rounded-2xl border border-[var(--line-soft)] bg-white/82 px-4 py-3">
                <span className="text-xs font-semibold uppercase tracking-[0.18em] text-[var(--ink-subtle)]">
                  账号状态
                </span>
                <p className="mt-2 font-semibold text-[var(--ink-strong)]">
                  正常
                </p>
              </div>
            </div>
          </Panel>

          <Panel title="快捷偏好" eyebrow="Tags">
            <div className="flex flex-wrap gap-2">
              {user?.defaultDepartment && (
                <Badge tone="soft">
                  {user.defaultDepartment.name}
                </Badge>
              )}
              <Badge>{user?.defaultHospitalCode ?? "MAIN"}</Badge>
            </div>
          </Panel>
        </>
      }
    >
      {error ? (
        <div className="rounded-[1.2rem] border border-[rgba(190,92,67,0.18)] bg-[var(--danger-soft)] px-4 py-3 text-sm leading-6 text-[#8a4a3a]">
          {error}
        </div>
      ) : null}

      {message ? (
        <div className="rounded-[1.2rem] border border-[rgba(31,92,82,0.18)] bg-[var(--bg-soft)] px-4 py-3 text-sm leading-6 text-[var(--accent-main-strong)]">
          {message}
        </div>
      ) : null}

      <div className="grid gap-5 xl:grid-cols-2">
        <Panel title="基础资料" eyebrow="Identity">
          <div className="space-y-4">
            <div className="rounded-2xl border border-[var(--line-soft)] bg-white/82 px-4 py-4">
              <p className="text-xs font-semibold uppercase tracking-[0.18em] text-[var(--ink-subtle)]">
                姓名
              </p>
              <p className="mt-2 text-base font-semibold text-[var(--ink-strong)]">
                {user?.displayName ?? "-"}
              </p>
            </div>
            <div className="rounded-2xl border border-[var(--line-soft)] bg-white/82 px-4 py-4">
              <p className="text-xs font-semibold uppercase tracking-[0.18em] text-[var(--ink-subtle)]">
                手机号
              </p>
              <p className="mt-2 text-base font-semibold text-[var(--ink-strong)]">
                {user?.mobileMasked ?? "未绑定"}
              </p>
            </div>
            <div className="rounded-2xl border border-[var(--line-soft)] bg-white/82 px-4 py-4">
              <p className="text-xs font-semibold uppercase tracking-[0.18em] text-[var(--ink-subtle)]">
                邮箱
              </p>
              <p className="mt-2 text-base font-semibold text-[var(--ink-strong)]">
                {user?.emailMasked ?? "未绑定"}
              </p>
            </div>
          </div>
        </Panel>

        <Panel title="就诊偏好" eyebrow="Preferences">
          <form onSubmit={handleProfileUpdate}>
            <div className="space-y-4">
              <label className="block">
                <span className="text-sm font-semibold text-[var(--ink-body)]">
                  显示名称
                </span>
                <input
                  className="mt-2 w-full rounded-[1.1rem] border border-[var(--line-soft)] bg-white/80 px-4 py-3 text-sm text-[var(--ink-strong)] outline-none transition focus:border-[var(--accent-main)]"
                  type="text"
                  value={profileForm.displayName}
                  onChange={(e) =>
                    setProfileForm((prev) => ({
                      ...prev,
                      displayName: e.target.value,
                    }))
                  }
                />
              </label>

              <label className="block">
                <span className="text-sm font-semibold text-[var(--ink-body)]">
                  默认院区编码
                </span>
                <input
                  className="mt-2 w-full rounded-[1.1rem] border border-[var(--line-soft)] bg-white/80 px-4 py-3 text-sm text-[var(--ink-strong)] outline-none transition focus:border-[var(--accent-main)]"
                  type="text"
                  value={profileForm.defaultHospitalCode}
                  onChange={(e) =>
                    setProfileForm((prev) => ({
                      ...prev,
                      defaultHospitalCode: e.target.value,
                    }))
                  }
                />
              </label>

              <label className="block">
                <span className="text-sm font-semibold text-[var(--ink-body)]">
                  默认科室
                </span>
                <select
                  className="mt-2 w-full rounded-[1.1rem] border border-[var(--line-soft)] bg-white/80 px-4 py-3 text-sm text-[var(--ink-strong)] outline-none transition focus:border-[var(--accent-main)]"
                  value={profileForm.defaultDepartmentId}
                  onChange={(e) =>
                    setProfileForm((prev) => ({
                      ...prev,
                      defaultDepartmentId: e.target.value,
                    }))
                  }
                >
                  <option value="">不设置默认科室</option>
                  {departments.map((dept) => (
                    <option key={dept.departmentId} value={dept.departmentId}>
                      {dept.name}
                    </option>
                  ))}
                </select>
              </label>
            </div>

            <button
              className="mt-5 w-full rounded-full bg-[var(--accent-main)] px-5 py-3 text-sm font-semibold text-white transition hover:bg-[var(--accent-main-strong)] disabled:opacity-60"
              disabled={isBusy}
              type="submit"
            >
              {isBusy ? "保存中..." : "保存偏好"}
            </button>
          </form>
        </Panel>
      </div>
    </WorkspaceShell>
  );
}

function LoadingSkeleton() {
  return (
    <main className="min-h-screen px-4 py-5 sm:px-6">
      <div className="mx-auto max-w-[1520px]">
        <div className="grid gap-5 xl:grid-cols-[280px_minmax(0,1fr)_320px]">
          <div className="h-[calc(100vh-2.5rem)] animate-pulse rounded-[2rem] bg-slate-200" />
          <div className="space-y-5">
            <div className="h-48 animate-pulse rounded-[1.75rem] bg-slate-100" />
            <div className="grid gap-5 xl:grid-cols-2">
              <div className="h-64 animate-pulse rounded-[1.75rem] bg-slate-100" />
              <div className="h-64 animate-pulse rounded-[1.75rem] bg-slate-100" />
            </div>
          </div>
          <div className="space-y-5">
            <div className="h-36 animate-pulse rounded-[1.75rem] bg-slate-100" />
            <div className="h-28 animate-pulse rounded-[1.75rem] bg-slate-100" />
          </div>
        </div>
      </div>
    </main>
  );
}
