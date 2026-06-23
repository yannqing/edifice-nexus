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

const PROJECT_DETAIL_PATHS = new Set([
  "/project-lifecycle",
]);

const HIDDEN_ROUTE_PERMISSIONS: Record<string, string> = {
  "/acceptance": "menu:oa-applications",
  "/performance-restore": "menu:performance-restore",
};

const PUBLIC_DASHBOARD_PATHS = new Set([
  "/profile",
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
  const hiddenPermissionCode = useMemo(() => {
    return Object.entries(HIDDEN_ROUTE_PERMISSIONS)
      .sort(([a], [b]) => b.length - a.length)
      .find(([path]) => pathname === path || pathname.startsWith(`${path}/`))?.[1];
  }, [pathname]);

  const messageDetailAccess = isHydrated
    && typeof window !== "undefined"
    && APPROVAL_DETAIL_PATHS.has(pathname)
    && new URLSearchParams(window.location.search).has("detailId");
  const projectDetailAccess = isHydrated
    && typeof window !== "undefined"
    && PROJECT_DETAIL_PATHS.has(pathname)
    && new URLSearchParams(window.location.search).has("projectId");
  const configuredPermissionCode = matchedItem?.permissionCode ?? hiddenPermissionCode;
  const allowed = PUBLIC_DASHBOARD_PATHS.has(pathname)
    || messageDetailAccess
    || projectDetailAccess
    || (configuredPermissionCode
      ? hasPermission(permissions, configuredPermissionCode, roles)
      : false);

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
