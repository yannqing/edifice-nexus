import { toast } from "sonner";
import { BaseResponse, ResponseCode } from "@/types/api";
import { getAccessToken, clearAuth } from "@/lib/token";

const BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL ?? "";

type RequestOptions = Omit<RequestInit, "body"> & {
  params?: Record<string, string>;
  body?: unknown;
};

async function request<T>(
  url: string,
  options: RequestOptions = {}
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

  // Token 过期处理
  if (
    data.code === ResponseCode.ACCESS_TOKEN_EXPIRE ||
    data.code === ResponseCode.TOKEN_AUTHENTICATE_FAILURE
  ) {
    clearAuth();
    toast.error("登录已过期，请重新登录");
    window.location.href = "/login";
    throw new Error(data.msg || "登录已过期，请重新登录");
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

export default request;
