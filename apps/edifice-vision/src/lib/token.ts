const ACCESS_TOKEN_KEY = "access_token";
const REFRESH_TOKEN_KEY = "refresh_token";
const USER_INFO_KEY = "user_info";
const USER_ROLES_KEY = "user_roles";

// ==================== Token ====================

export function getAccessToken(): string | null {
  if (typeof window === "undefined") return null;
  return localStorage.getItem(ACCESS_TOKEN_KEY);
}

export function getRefreshToken(): string | null {
  if (typeof window === "undefined") return null;
  return localStorage.getItem(REFRESH_TOKEN_KEY);
}

export function setTokens(accessToken: string, refreshToken: string): void {
  localStorage.setItem(ACCESS_TOKEN_KEY, accessToken);
  localStorage.setItem(REFRESH_TOKEN_KEY, refreshToken);
  // 同步到 cookie，供 Next.js middleware 读取
  document.cookie = `access_token=${accessToken}; path=/; max-age=${7 * 24 * 60 * 60}; SameSite=Lax`;
}

export function clearTokens(): void {
  localStorage.removeItem(ACCESS_TOKEN_KEY);
  localStorage.removeItem(REFRESH_TOKEN_KEY);
  // 清除 cookie
  document.cookie = "access_token=; path=/; max-age=0";
}

// ==================== 用户信息 ====================

export function getUserInfo<T>(): T | null {
  if (typeof window === "undefined") return null;
  const raw = localStorage.getItem(USER_INFO_KEY);
  if (!raw) return null;
  try {
    return JSON.parse(raw) as T;
  } catch {
    return null;
  }
}

export function setUserInfo<T>(user: T): void {
  localStorage.setItem(USER_INFO_KEY, JSON.stringify(user));
}

export function getUserRoles<T>(): T | null {
  if (typeof window === "undefined") return null;
  const raw = localStorage.getItem(USER_ROLES_KEY);
  if (!raw) return null;
  try {
    return JSON.parse(raw) as T;
  } catch {
    return null;
  }
}

export function setUserRoles<T>(roles: T): void {
  localStorage.setItem(USER_ROLES_KEY, JSON.stringify(roles));
}

// ==================== 清除所有认证数据 ====================

export function clearAuth(): void {
  clearTokens();
  localStorage.removeItem(USER_INFO_KEY);
  localStorage.removeItem(USER_ROLES_KEY);
}
