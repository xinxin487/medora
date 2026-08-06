"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { getAccessToken, clearTokens } from "@/lib/auth/token";
import { apiFetch } from "@/lib/api/client";
import type { CurrentUser } from "@/lib/types/api";

export function useAuth(requiredRole?: "USER" | "ADMIN") {
  const router = useRouter();
  const [user, setUser] = useState<CurrentUser | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    const token = getAccessToken();
    if (!token) {
      router.replace("/login");
      return;
    }

    apiFetch<CurrentUser>("/users/me")
      .then((u) => {
        setUser(u);
        setIsLoading(false);
      })
      .catch(() => {
        clearTokens();
        router.replace("/login");
      });
  }, [router]);

  useEffect(() => {
    if (!user || !requiredRole) return;
    if (!user.roles.includes(requiredRole)) {
      if (user.roles.includes("ADMIN")) {
        router.replace("/admin");
      } else {
        router.replace("/app/dashboard");
      }
    }
  }, [user, requiredRole, router]);

  return { user, isLoading, isAuthenticated: !!user };
}
