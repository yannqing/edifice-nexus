"use client";

import { useEffect, useRef } from "react";
import { useRouter } from "next/navigation";
import { Loader2 } from "lucide-react";
import { toast } from "sonner";
import { loginWithOaSso, isLoginSuccess } from "@/services/auth";
import { useAuth } from "@/store/auth-context";

export default function OaSsoPage() {
  const router = useRouter();
  const { setAuth } = useAuth();
  const started = useRef(false);

  useEffect(() => {
    if (started.current) return;
    started.current = true;
    const token = new URLSearchParams(window.location.search).get("token");
    if (!token) {
      toast.error("OA 登录凭证缺失");
      router.replace("/login");
      return;
    }

    loginWithOaSso(token)
      .then((response) => {
        if (!isLoginSuccess(response) || !response.data) {
          throw new Error(response.msg || "OA 单点登录失败");
        }
        const { accessToken, refreshToken, userInfo, roles, permissions } = response.data;
        setAuth(accessToken, refreshToken, userInfo, roles, permissions ?? []);
        router.replace("/");
      })
      .catch((error) => {
        toast.error(error instanceof Error ? error.message : "OA 单点登录失败");
        router.replace("/login");
      });
  }, [router, setAuth]);

  return (
    <main className="min-h-screen flex items-center justify-center bg-slate-50">
      <div className="flex items-center gap-3 text-slate-600">
        <Loader2 className="h-5 w-5 animate-spin text-blue-600" />
        <span>正在从 OA 登录 Edifice...</span>
      </div>
    </main>
  );
}
