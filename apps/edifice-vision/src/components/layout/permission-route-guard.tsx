"use client";

import { useEffect, useMemo } from "react";
import { usePathname, useRouter } from "next/navigation";
import { toast } from "sonner";
import { navigationConfig } from "@/data/mock-data";
import { hasPermission } from "@/lib/permissions";
import { useAuth } from "@/store/auth-context";

const APPROVAL_DETAIL_PATHS = new Set([
  "/inspection-approval",
  "/project-files/approval",
  "/bids",
  "/acceptance",
  "/output-value",
  "/timesheet",
  "/oa/applications",
]);

export function PermissionRouteGuard({ children }: { children: React.ReactNode }) {
  const pathname = usePathname();
  const router = useRouter();
  const { isAuthenticated, isHydrated, accessToken, roles, permissions } = useAuth();

  const matchedItem = useMemo(() => {
    return navigationConfig
      .flatMap((section) => section.items)
      .sort((a, b) => b.href.length - a.href.length)
      .find((item) =>
        item.href === pathname || (item.href !== "/" && pathname.startsWith(`${item.href}/`))
      );
  }, [pathname]);

  const messageDetailAccess = isHydrated
    && typeof window !== "undefined"
    && APPROVAL_DETAIL_PATHS.has(pathname)
    && new URLSearchParams(window.location.search).has("detailId");
  const allowed = messageDetailAccess || hasPermission(permissions, matchedItem?.permissionCode, roles);

  useEffect(() => {
    if (!isHydrated || !isAuthenticated || !accessToken) return;
    if (!allowed) {
      toast.error("暂无权限访问该功能");
      router.replace("/");
    }
  }, [accessToken, allowed, isAuthenticated, isHydrated, router]);

  if (!isHydrated || !isAuthenticated || !accessToken) return null;
  if (!allowed) return null;
  return <>{children}</>;
}
