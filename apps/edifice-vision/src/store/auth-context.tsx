"use client";

import {
  createContext,
  useContext,
  useState,
  useEffect,
  useCallback,
  type ReactNode,
} from "react";
import { toast } from "sonner";
import type { AuthState, SysUser, SysRole } from "@/types/auth";
import { get } from "@/lib/request";
import { ResponseCode } from "@/types/api";
import {
  getAccessToken,
  getRefreshToken,
  setTokens,
  setUserInfo,
  setUserRoles,
  setUserPermissions,
  getUserInfo,
  getUserRoles,
  getUserPermissions,
  clearAuth,
} from "@/lib/token";

type CurrentPermissionsData = {
  roles?: SysRole[];
  permissions?: string[];
};

interface AuthContextValue extends AuthState {
  /** 客户端是否已经完成本地认证缓存读取 */
  isHydrated: boolean;
  /** 登录成功后调用，存储认证数据 */
  setAuth: (
    accessToken: string,
    refreshToken: string,
    user: SysUser,
    roles: SysRole[],
    permissions?: string[]
  ) => void;
  /** 个人中心保存资料后调用，同步更新本地缓存与 Context */
  updateUser: (user: SysUser) => void;
  /** 退出登录，清除认证数据 */
  logout: () => void;
}

const AuthContext = createContext<AuthContextValue | null>(null);
let permissionChangeLogoutInProgress = false;

const EMPTY_AUTH_STATE: AuthState = {
  isAuthenticated: false,
  user: null,
  roles: [],
  permissions: [],
  accessToken: null,
  refreshToken: null,
};

function permissionSignature(roles: SysRole[] = [], permissions: string[] = []): string {
  const roleCodes = roles.map((role) => role.roleCode).filter(Boolean).sort();
  const permissionCodes = [...permissions].filter(Boolean).sort();
  return JSON.stringify({ roleCodes, permissionCodes });
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const readStoredState = (): AuthState => {
    const accessToken = getAccessToken();
    const refreshToken = getRefreshToken();
    const user = getUserInfo<SysUser>();
    const roles = getUserRoles<SysRole[]>();
    const permissions = getUserPermissions();

    if (accessToken && user) {
      return {
        isAuthenticated: true,
        user,
        roles: roles ?? [],
        permissions,
        accessToken,
        refreshToken,
      };
    }
    return {
      isAuthenticated: false,
      user: null,
      roles: [],
      permissions: [],
      accessToken: null,
      refreshToken: null,
    };
  };

  const [state, setState] = useState<AuthState>(EMPTY_AUTH_STATE);
  const [isHydrated, setIsHydrated] = useState(false);

  useEffect(() => {
    const handleAuthUpdated = () => {
      setState(readStoredState());
      setIsHydrated(true);
    };
    handleAuthUpdated();
    window.addEventListener("auth:updated", handleAuthUpdated);
    return () => window.removeEventListener("auth:updated", handleAuthUpdated);
  }, []);

  useEffect(() => {
    if (!state.isAuthenticated || !state.accessToken) return;

    let cancelled = false;
    let checking = false;

    const checkPermissionSnapshot = async () => {
      if (checking || document.hidden || permissionChangeLogoutInProgress) return;
      checking = true;
      try {
        const latestState = readStoredState();
        if (!latestState.isAuthenticated || !latestState.accessToken) return;
        const res = await get<CurrentPermissionsData>("/auth/current-permissions", {
          toastOnBizError: false,
        });
        if (cancelled || res.code !== ResponseCode.SUCCESS || !res.data) return;

        const current = permissionSignature(latestState.roles, latestState.permissions);
        const next = permissionSignature(res.data.roles ?? [], res.data.permissions ?? []);
        if (current !== next) {
          permissionChangeLogoutInProgress = true;
          clearAuth();
          setState({
            isAuthenticated: false,
            user: null,
            roles: [],
            permissions: [],
            accessToken: null,
            refreshToken: null,
          });
          toast.error("您的相关信息已经修改，请您重新登陆。");
          window.setTimeout(() => {
            window.location.href = "/login";
          }, 1200);
        }
      } catch {
        // request.ts 已统一处理认证失败；这里避免轮询错误打扰正常操作。
      } finally {
        checking = false;
      }
    };

    const firstCheckTimer = window.setTimeout(checkPermissionSnapshot, 3000);
    const interval = window.setInterval(checkPermissionSnapshot, 60000);
    window.addEventListener("focus", checkPermissionSnapshot);
    return () => {
      cancelled = true;
      window.clearTimeout(firstCheckTimer);
      window.clearInterval(interval);
      window.removeEventListener("focus", checkPermissionSnapshot);
    };
  }, [state.accessToken, state.isAuthenticated]);

  const setAuth = useCallback(
    (
      accessToken: string,
      refreshToken: string,
      user: SysUser,
      roles: SysRole[],
      permissions: string[] = []
    ) => {
      setTokens(accessToken, refreshToken);
      setUserInfo(user);
      setUserRoles(roles);
      setUserPermissions(permissions);
      permissionChangeLogoutInProgress = false;
      setState({
        isAuthenticated: true,
        user,
        roles,
        permissions,
        accessToken,
        refreshToken,
      });
    },
    []
  );

  const updateUser = useCallback((user: SysUser) => {
    setUserInfo(user);
    setState((prev) => ({ ...prev, user }));
  }, []);

  const logout = useCallback(() => {
    clearAuth();
    permissionChangeLogoutInProgress = false;
    setState({
      isAuthenticated: false,
      user: null,
      roles: [],
      permissions: [],
      accessToken: null,
      refreshToken: null,
    });
  }, []);

  return (
    <AuthContext.Provider value={{ ...state, isHydrated, setAuth, updateUser, logout }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error("useAuth must be used within an AuthProvider");
  }
  return context;
}
