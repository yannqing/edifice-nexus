"use client";

import {
  createContext,
  useContext,
  useState,
  useEffect,
  useCallback,
  type ReactNode,
} from "react";
import type { AuthState, SysUser, SysRole } from "@/types/auth";
import {
  getAccessToken,
  getRefreshToken,
  setTokens,
  setUserInfo,
  setUserRoles,
  getUserInfo,
  getUserRoles,
  clearAuth,
} from "@/lib/token";

interface AuthContextValue extends AuthState {
  /** 登录成功后调用，存储认证数据 */
  setAuth: (
    accessToken: string,
    refreshToken: string,
    user: SysUser,
    roles: SysRole[]
  ) => void;
  /** 个人中心保存资料后调用，同步更新本地缓存与 Context */
  updateUser: (user: SysUser) => void;
  /** 退出登录，清除认证数据 */
  logout: () => void;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<AuthState>({
    isAuthenticated: false,
    user: null,
    roles: [],
    accessToken: null,
    refreshToken: null,
  });

  // 初始化时从 localStorage 恢复认证状态
  useEffect(() => {
    const accessToken = getAccessToken();
    const refreshToken = getRefreshToken();
    const user = getUserInfo<SysUser>();
    const roles = getUserRoles<SysRole[]>();

    if (accessToken && user) {
      setState({
        isAuthenticated: true,
        user,
        roles: roles ?? [],
        accessToken,
        refreshToken,
      });
    }
  }, []);

  const setAuth = useCallback(
    (
      accessToken: string,
      refreshToken: string,
      user: SysUser,
      roles: SysRole[]
    ) => {
      setTokens(accessToken, refreshToken);
      setUserInfo(user);
      setUserRoles(roles);
      setState({
        isAuthenticated: true,
        user,
        roles,
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
    setState({
      isAuthenticated: false,
      user: null,
      roles: [],
      accessToken: null,
      refreshToken: null,
    });
  }, []);

  return (
    <AuthContext.Provider value={{ ...state, setAuth, updateUser, logout }}>
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
