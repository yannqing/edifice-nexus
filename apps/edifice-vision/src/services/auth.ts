import { post } from "@/lib/request";
import { BaseResponse, ResponseCode } from "@/types/api";
import type { LoginParams, LoginResponseData } from "@/types/auth";

/**
 * 登录接口
 *
 * 后端 Spring Security 使用 formLogin，
 * 需要以 application/x-www-form-urlencoded 格式发送 username 和 password。
 */
export async function login(
  params: LoginParams
): Promise<BaseResponse<LoginResponseData>> {
  const formData = new URLSearchParams();
  formData.append("username", params.username);
  formData.append("password", params.password);

  return post<LoginResponseData>("/auth/login", {
    body: formData,
    headers: {
      "Content-Type": "application/x-www-form-urlencoded",
    },
  });
}

/**
 * 判断登录是否成功
 */
export function isLoginSuccess(response: BaseResponse<unknown>): boolean {
  return response.code === ResponseCode.LOGIN_SUCCESS;
}
