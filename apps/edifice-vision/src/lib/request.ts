import { toast } from "sonner";
import { BaseResponse, ResponseCode } from "@/types/api";
import { getAccessToken, getRefreshToken, setTokens, clearAuth } from "@/lib/token";

const BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL ?? "";

/** 需要被视为"需重新登录"的 code 列表 */
const AUTH_FAILED_CODES: readonly number[] = [
  ResponseCode.TOKEN_ERROR,
  ResponseCode.TOKEN_AUTHENTICATE_FAILURE,
  ResponseCode.REFRESH_TOKEN_EXPIRE,
];

/** 进行中的刷新 Promise，防止并发重复刷新 */
let refreshPromise: Promise<string | null> | null = null;

async function doRefresh(): Promise<string | null> {
  const refreshToken = getRefreshToken();
  if (!refreshToken) return null;

  try {
    const res = await fetch(`${BASE_URL}/auth/refresh`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ refreshToken }),
    });
    if (!res.ok) return null;
    const data: BaseResponse<{ accessToken: string }> = await res.json();
    if (data.code === ResponseCode.SUCCESS && data.data?.accessToken) {
      setTokens(data.data.accessToken, refreshToken);
      return data.data.accessToken;
    }
    return null;
  } catch {
    return null;
  }
}

/** 触发强制登出：清理本地状态并跳登录页 */
function forceLogout(msg: string): never {
  clearAuth();
  toast.error(msg);
  window.location.href = "/login";
  throw new Error(msg);
}

type RequestOptions = Omit<RequestInit, "body"> & {
  params?: Record<string, string>;
  body?: unknown;
};

async function request<T>(
  url: string,
  options: RequestOptions = {},
  _retried = false
): Promise<BaseResponse<T>> {
  const { params, body, headers: customHeaders, ...rest } = options;

  // 构建完整 URL
  let fullUrl = `${BASE_URL}${url}`;
  if (params) {
    const searchParams = new URLSearchParams(params);
    fullUrl += `?${searchParams.toString()}`;
  }

  // 构建请求头
  const headers = new Headers(customHeaders);
  const token = getAccessToken();
  if (token) {
    headers.set("token", token);
  }

  // 默认 JSON content-type（如果 body 是对象且未指定 content-type）
  // FormData 不设置 Content-Type，让浏览器自动处理 boundary
  const isFormData = typeof FormData !== "undefined" && body instanceof FormData;
  if (body && !isFormData && !headers.has("Content-Type")) {
    headers.set("Content-Type", "application/json");
  }

  const response = await fetch(fullUrl, {
    ...rest,
    headers,
    body: body
      ? headers.get("Content-Type")?.includes("application/json")
        ? JSON.stringify(body)
        : (body as BodyInit)
      : undefined,
  });

  if (!response.ok) {
    throw new Error(`HTTP error: ${response.status}`);
  }

  const data: BaseResponse<T> = await response.json();

  // Access Token 过期：尝试使用 Refresh Token 静默刷新，然后重放一次原请求
  if (data.code === ResponseCode.ACCESS_TOKEN_EXPIRE && !_retried) {
    refreshPromise = refreshPromise ?? doRefresh();
    const newToken = await refreshPromise;
    refreshPromise = null;
    if (newToken) {
      return request<T>(url, options, true);
    }
    forceLogout("登录已过期，请重新登录");
  }

  // 其他认证失败：无法自愈，直接登出
  if (AUTH_FAILED_CODES.includes(data.code) || data.code === ResponseCode.ACCESS_TOKEN_EXPIRE) {
    forceLogout(data.msg || "登录已过期，请重新登录");
  }

  return data;
}

// ==================== 快捷方法 ====================

export function get<T>(url: string, options?: RequestOptions) {
  return request<T>(url, { ...options, method: "GET" });
}

export function post<T>(url: string, options?: RequestOptions) {
  return request<T>(url, { ...options, method: "POST" });
}

export function put<T>(url: string, options?: RequestOptions) {
  return request<T>(url, { ...options, method: "PUT" });
}

export function del<T>(url: string, options?: RequestOptions) {
  return request<T>(url, { ...options, method: "DELETE" });
}

/**
 * 判断错误是否为请求取消（AbortError）
 * 用于列表页请求竞态处理，被取消的请求不应更新状态
 */
export function isAbortError(error: unknown): boolean {
  return error instanceof DOMException && error.name === "AbortError";
}

export default request;
