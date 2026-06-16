"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import {
  LayoutDashboard,
  FolderOpen,
  ClipboardCheck,
  Clock,
  Briefcase,
  FileText,
  Coins,
  Wallet,
  BarChart3,
  UserCircle,
  Megaphone,
  Users,
  Users2,
  RefreshCcw,
  FolderCheck,
  BadgeCheck,
  Target,
  LogOut,
  ClipboardList,
  History,
  Bell,
  GitBranch,
  ListTodo,
  SlidersHorizontal,
} from "lucide-react";
import { cn } from "@/lib/utils";
import { Avatar, AvatarImage, AvatarFallback } from "@/components/ui/avatar";
import { Badge } from "@/components/ui/badge";
import { ScrollArea } from "@/components/ui/scroll-area";
import { toast } from "sonner";
import { navigationConfig } from "@/data/mock-data";
import { useAuth } from "@/store/auth-context";
import { post } from "@/lib/request";
import { getMyPendingCounts } from "@/services/approval-flow";
import { getUnreadMessageCount } from "@/services/message-center";
import { ResponseCode } from "@/types/api";
import { hasPermission } from "@/lib/permissions";

/**
 * 侧边栏 item.id -> 业务类型 ext 的映射。
 * 未列入的 item 不显示审批 badge。
 */
const ITEM_BIZ_TYPE: Record<string, string> = {
  "inspection-approval": "inspection",
  "project-files-approval": "file",
  acceptance: "acceptance",
  bids: "bid",
  "oa-applications": "oa_application",
};

// Icon mapping
const iconMap: Record<string, React.ComponentType<{ className?: string }>> = {
  LayoutDashboard,
  FolderOpen,
  ClipboardCheck,
  Clock,
  Briefcase,
  FileText,
  Coins,
  Wallet,
  BarChart3,
  UserCircle,
  Megaphone,
  Users,
  Users2,
  RefreshCcw,
  FolderCheck,
  BadgeCheck,
  Target,
  ClipboardList,
  History,
  Bell,
  GitBranch,
  ListTodo,
  SlidersHorizontal,
};

interface SidebarProps {
  /** 移动端抽屉是否展开 */
  mobileOpen?: boolean;
  /** 关闭抽屉回调 */
  onMobileClose?: () => void;
}

export function Sidebar({ mobileOpen = false, onMobileClose }: SidebarProps = {}) {
  const pathname = usePathname();
  const router = useRouter();
  const { user, roles, permissions, isAuthenticated, isHydrated, logout } = useAuth();

  const [pendingCounts, setPendingCounts] = useState<Record<string, number>>({});
  const [unreadMessageCount, setUnreadMessageCount] = useState(0);

  const fetchPendingCounts = useCallback(async (signal?: AbortSignal) => {
    try {
      const [pendingRes, messageRes] = await Promise.all([
        getMyPendingCounts(signal),
        getUnreadMessageCount(signal),
      ]);
      if (pendingRes.code === ResponseCode.SUCCESS && pendingRes.data) setPendingCounts(pendingRes.data);
      if (messageRes.code === ResponseCode.SUCCESS) setUnreadMessageCount(messageRes.data ?? 0);
    } catch {
      // 静默
    }
  }, []);

  // 挂载 + 路由切换时刷新（路由切换往往意味着刚完成了一次审批 / 上传）
  useEffect(() => {
    if (!isHydrated || !isAuthenticated) return;
    const controller = new AbortController();
    fetchPendingCounts(controller.signal);
    const refresh = () => fetchPendingCounts();
    window.addEventListener("message-center:updated", refresh);
    return () => {
      controller.abort();
      window.removeEventListener("message-center:updated", refresh);
    };
  }, [fetchPendingCounts, isAuthenticated, isHydrated, pathname]);

  // 路由切换时自动关闭移动抽屉
  useEffect(() => {
    onMobileClose?.();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [pathname]);

  const handleLogout = async () => {
    try {
      await post("/auth/logout", {});
    } catch {
      // 即使后端请求失败也清除本地状态
    }
    logout();
    toast.success("已安全退出登录");
    router.push("/login");
  };

  return (
    <>
      {/* 移动端遮罩 */}
      {mobileOpen && (
        <div
          className="md:hidden fixed inset-0 bg-black/50 z-40"
          onClick={onMobileClose}
          aria-hidden="true"
        />
      )}
      <aside
        className={cn(
          "w-full sm:w-64 bg-white border-r border-slate-200 flex flex-col",
          // mobile: 固定定位抽屉
          "fixed inset-y-0 left-0 z-50 h-screen overflow-hidden transition-transform",
          // desktop: 回到 sticky 流内布局
          "md:sticky md:top-0 md:translate-x-0 md:z-0",
          mobileOpen ? "translate-x-0" : "-translate-x-full md:translate-x-0"
        )}
      >
      {/* Logo */}
      <div className="flex-shrink-0 p-6 flex items-center gap-3">
        <div className="w-8 h-8 bg-blue-600 rounded-lg flex items-center justify-center text-white font-bold">
          R
        </div>
        <span className="font-bold text-slate-800 text-lg tracking-tight">
          然而信工程管理
        </span>
      </div>

      {/* Navigation */}
      <ScrollArea className="flex-1 min-h-0">
        <nav className="px-4 space-y-1 pb-4">
          {navigationConfig.map((section) => {
            const visibleItems = section.items.filter((item) =>
              hasPermission(permissions, item.permissionCode, roles)
            );
            if (visibleItems.length === 0) return null;

            return (
            <div key={section.id}>
              {section.title && (
                <div className="text-xs text-slate-400 px-4 pt-4 pb-2 uppercase tracking-wider font-semibold">
                  {section.title}
                </div>
              )}
              {visibleItems.map((item) => {
                const Icon = iconMap[item.icon];
                const isActive = pathname === item.href;

                return (
                  <Link
                    key={item.id}
                    href={item.href}
                    className={cn(
                      "flex items-center gap-3 px-4 py-3 rounded-xl cursor-pointer transition-all",
                      isActive
                        ? "sidebar-item-active"
                        : "text-slate-500 hover:bg-slate-50"
                    )}
                  >
                    {Icon && (
                      <Icon
                        className={cn(
                          "w-5 h-5",
                          isActive ? "text-blue-600" : "text-slate-400"
                        )}
                      />
                    )}
                    <span
                      className={cn(
                        "font-medium text-sm",
                        isActive ? "text-blue-600" : "text-slate-600"
                      )}
                    >
                      {item.label}
                    </span>
                    {(() => {
                      const bizType = ITEM_BIZ_TYPE[item.id];
                      const dynamicBadge = bizType ? pendingCounts[bizType] ?? 0 : 0;
                      const todoCenterCount = Object.values(pendingCounts).reduce((sum, count) => sum + count, 0);
                      const badgeValue = item.id === "message-center"
                        ? unreadMessageCount
                        : item.id === "todo-center"
                          ? todoCenterCount
                          : dynamicBadge > 0 ? dynamicBadge : item.badge;
                      if (!badgeValue) return null;
                      return (
                        <Badge
                          variant="secondary"
                          className="ml-auto text-xs bg-rose-100 text-rose-600 hover:bg-rose-100"
                        >
                          {badgeValue}
                        </Badge>
                      );
                    })()}
                  </Link>
                );
              })}
            </div>
            );
          })}
        </nav>
      </ScrollArea>

      {/* User Profile */}
      <div className="flex-shrink-0 p-4 border-t border-slate-100">
        <div className="flex items-center gap-3 p-2">
          <button
            onClick={() => router.push("/profile")}
            className="flex items-center gap-3 flex-1 min-w-0 rounded-lg hover:bg-slate-50 transition-colors p-1 -m-1 cursor-pointer"
            title="个人中心"
          >
            <Avatar className="w-10 h-10">
              {user?.avatar ? <AvatarImage src={user.avatar} alt={user.realName ?? user.username} /> : null}
              <AvatarFallback>
                {(user?.realName ?? user?.username ?? "U").charAt(0)}
              </AvatarFallback>
            </Avatar>
            <div className="flex-1 overflow-hidden text-left">
              <p className="text-sm font-semibold text-slate-800 truncate">
                {user?.realName || user?.username || "用户"}
              </p>
              <p className="text-xs text-slate-400 truncate">
                {user?.email || user?.phone || ""}
              </p>
            </div>
          </button>
          <button
            onClick={handleLogout}
            className="p-1.5 text-slate-400 hover:text-rose-500 hover:bg-rose-50 rounded-lg transition-colors shrink-0"
            title="退出登录"
          >
            <LogOut className="w-4 h-4" />
          </button>
        </div>
      </div>
      </aside>
    </>
  );
}
