import type { SysRole } from "@/types/auth";

const SUPER_ROLE_CODES = new Set(["SUPER_ADMIN", "ADMIN", "admin"]);

export function isSuperAdmin(roles: SysRole[] | undefined): boolean {
  return (roles ?? []).some((role) => SUPER_ROLE_CODES.has(role.roleCode));
}

export function hasPermission(
  permissions: string[] | undefined,
  permissionCode: string | undefined,
  roles?: SysRole[]
): boolean {
  if (!permissionCode) return true;
  if (isSuperAdmin(roles)) return true;
  return (permissions ?? []).includes(permissionCode);
}
