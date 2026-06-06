"use client";

import { useState } from "react";
import { Menu } from "lucide-react";
import { Sidebar } from "@/components/layout/sidebar";
import { PermissionRouteGuard } from "@/components/layout/permission-route-guard";

export default function DashboardLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  const [mobileOpen, setMobileOpen] = useState(false);
  return (
    <div className="flex min-h-screen">
      <Sidebar mobileOpen={mobileOpen} onMobileClose={() => setMobileOpen(false)} />
      <div className="flex-1 flex flex-col min-w-0">
        {/* 移动端顶部栏 */}
        <header className="md:hidden flex items-center gap-3 px-4 py-3 border-b border-slate-100 bg-white sticky top-0 z-30">
          <button
            type="button"
            onClick={() => setMobileOpen(true)}
            className="p-2 -ml-2 rounded-lg hover:bg-slate-100"
            aria-label="打开菜单"
          >
            <Menu className="w-5 h-5 text-slate-600" />
          </button>
          <span className="font-semibold text-slate-800 tracking-tight">
            然而信工程管理
          </span>
        </header>
        <main className="flex-1 overflow-y-auto min-w-0">
          <PermissionRouteGuard>{children}</PermissionRouteGuard>
        </main>
      </div>
    </div>
  );
}
