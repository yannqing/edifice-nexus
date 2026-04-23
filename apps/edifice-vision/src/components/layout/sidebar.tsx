"use client";

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
  LogOut,
} from "lucide-react";
import { cn } from "@/lib/utils";
import { Avatar, AvatarImage, AvatarFallback } from "@/components/ui/avatar";
import { Badge } from "@/components/ui/badge";
import { ScrollArea } from "@/components/ui/scroll-area";
import { toast } from "sonner";
import { navigationConfig } from "@/data/mock-data";
import { useAuth } from "@/store/auth-context";
import { post } from "@/lib/request";

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
};

export function Sidebar() {
  const pathname = usePathname();
  const router = useRouter();
  const { user, logout } = useAuth();

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
    <aside className="w-64 bg-white border-r border-slate-200 flex flex-col sticky top-0 h-screen overflow-hidden">
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
          {navigationConfig.map((section) => (
            <div key={section.id}>
              {section.title && (
                <div className="text-xs text-slate-400 px-4 pt-4 pb-2 uppercase tracking-wider font-semibold">
                  {section.title}
                </div>
              )}
              {section.items.map((item) => {
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
                    {item.badge && (
                      <Badge
                        variant="secondary"
                        className="ml-auto text-xs bg-rose-100 text-rose-600 hover:bg-rose-100"
                      >
                        {item.badge}
                      </Badge>
                    )}
                  </Link>
                );
              })}
            </div>
          ))}
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
  );
}
