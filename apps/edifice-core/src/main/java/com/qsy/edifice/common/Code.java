package com.qsy.edifice.common;

public class Code {

    public static final Integer SUCCESS = 200;              //一般的成功操作
    public static final Integer FAILURE = 500;              //一般的失败操作

    public static final Integer LOGIN_SUCCESS = 20001;      //登录成功
    public static final Integer LOGIN_FAILURE = 20000;      //登录失败
    public static final Integer LOGOUT_SUCCESS = 20010;     //退出成功

    public static final Integer TOKEN_EXPIRE = 10000;       //token过期（旧版，已废弃）
    public static final Integer TOKEN_AUTHENTICATE_FAILURE = 10001;       //token认证失败
    public static final Integer TOKEN_ERROR = 10002;       //token错误（无效或为空）
    public static final Integer PERMISSION_DENIED = 40300; //权限不足

    // 双 Token 策略专用状态码
    public static final Integer ACCESS_TOKEN_EXPIRE = 10003;       //Access Token 过期（需重新登录）
    public static final Integer REFRESH_TOKEN_EXPIRE = 10004;      //Refresh Token 过期（但 Access Token 可能仍有效，可尝试宽限期刷新）

    // 限流专用状态码
    public static final Integer TOO_MANY_REQUESTS = 42900;         //请求过于频繁（HTTP 429）

}
