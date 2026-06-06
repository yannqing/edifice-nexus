"use client";

import { useEffect, useMemo } from "react";
import { usePathname, useRouter } from "next/navigation";
import { toast } from "sonner";
import { navigationConfig } from "@/data/mock-data";
import { hasPermission } from "@/lib/permissions";
import { useAuth } from "@/store/auth-context";

export function PermissionRouteGuard({ children }: { children: React.ReactNode }) {
  const pathname = usePathname();
  const router = useRouter();
  const { isAuthenticated, isHydrated, accessToken, roles, permissions } = useAuth();

  const matchedItem = useMemo(() => {
    return navigationConfig
      .flatMap((section) => section.items)
      .find((item) => item.href === pathname);
  }, [pathname]);

  const allowed = hasPermission(permissions, matchedItem?.permissionCode, roles);

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
