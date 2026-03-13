"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
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
  LogOut,
} from "lucide-react";
import { cn } from "@/lib/utils";
import { Avatar, AvatarImage, AvatarFallback } from "@/components/ui/avatar";
import { Badge } from "@/components/ui/badge";
import { ScrollArea } from "@/components/ui/scroll-area";
import { navigationConfig, currentUser } from "@/data/mock-data";

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
};

export function Sidebar() {
  const pathname = usePathname();

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
        <div className="flex items-center gap-3 p-2 hover:bg-slate-50 rounded-xl cursor-pointer">
          <Avatar className="w-10 h-10">
            <AvatarImage src={currentUser.avatar} alt={currentUser.name} />
            <AvatarFallback>{currentUser.name.charAt(0)}</AvatarFallback>
          </Avatar>
          <div className="flex-1 overflow-hidden">
            <p className="text-sm font-semibold text-slate-800 truncate">
              {currentUser.name}
            </p>
            <p className="text-xs text-slate-400 truncate">{currentUser.role}</p>
          </div>
          <LogOut className="w-4 h-4 text-slate-400" />
        </div>
      </div>
    </aside>
  );
}
