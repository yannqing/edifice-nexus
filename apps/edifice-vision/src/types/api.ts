// 后端通用响应结构
export interface BaseResponse<T = unknown> {
  code: number;
  data: T;
  msg: string;
}

// 后端响应状态码
export const ResponseCode = {
  SUCCESS: 200,
  FAILURE: 500,

  // 认证相关
  LOGIN_SUCCESS: 20001,
  LOGIN_FAILURE: 20000,
  LOGOUT_SUCCESS: 20010,

  // Token 相关
  ACCESS_TOKEN_EXPIRE: 10003,
  REFRESH_TOKEN_EXPIRE: 10004,
  TOKEN_AUTHENTICATE_FAILURE: 10001,
  TOKEN_ERROR: 10002,

  // 限流
  TOO_MANY_REQUESTS: 42900,
} as const;

export type ResponseCodeValue =
  (typeof ResponseCode)[keyof typeof ResponseCode];
