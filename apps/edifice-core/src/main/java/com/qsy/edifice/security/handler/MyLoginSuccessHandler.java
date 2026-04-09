package com.qsy.edifice.security.handler;

import com.alibaba.fastjson2.JSON;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qsy.edifice.common.Code;
import com.qsy.edifice.domain.entity.SysRole;
import com.qsy.edifice.domain.entity.SysUser;
import com.qsy.edifice.domain.vo.LoginVo;
import com.qsy.edifice.security.SecurityUser;
import com.qsy.edifice.utils.JwtUtils;
import com.qsy.edifice.utils.RedisCache;
import com.qsy.edifice.utils.ResultUtils;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class MyLoginSuccessHandler implements AuthenticationSuccessHandler {


    @Resource
    private JwtUtils jwtUtils;

    private final RedisCache redisCache;


    public MyLoginSuccessHandler(RedisCache redisCache) {
        this.redisCache = redisCache;
    }

    /**
     * 登录成功处理器：返回用户信息，对应用户的权限信息，登录生成token
     * @param request 会话请求
     * @param response 会话响应
     * @param authentication 认证信息
     * @throws IOException IO 异常
     */
    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException {
        response.setContentType("application/json;charset=UTF-8");

        SecurityUser securityUser = (SecurityUser) authentication.getPrincipal();
        SysUser user = securityUser.getSysUser();
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        String userInfo = objectMapper.writeValueAsString(user);

        List<SysRole> roles = securityUser.getSysRole();

        // 生成双 token（Access Token + Refresh Token）
        String accessToken = jwtUtils.generateAccessToken(userInfo, JSON.toJSONString(roles));
        String refreshToken = jwtUtils.generateRefreshToken(String.valueOf(user.getUserId()));

        // 将双 token 存储到 Redis
        // Access Token: 7天过期
        redisCache.setCacheObject("access_token:" + accessToken, userInfo, 7, TimeUnit.DAYS);
        // Refresh Token: 2小时过期
        redisCache.setCacheObject("refresh_token:" + refreshToken, String.valueOf(user.getUserId()), 2, TimeUnit.HOURS);

        // 用户 token 映射（用于强制下线）
        java.util.Map<String, String> userTokens = new java.util.HashMap<>();
        userTokens.put("accessToken", accessToken);
        userTokens.put("refreshToken", refreshToken);
        redisCache.setCacheObject("user_tokens:" + user.getUserId(), JSON.toJSONString(userTokens), 7, TimeUnit.DAYS);

        // 构建返回的用户信息（userId 转为字符串，避免前端 JS 精度丢失）
        java.util.Map<String, Object> userInfoMap = new java.util.LinkedHashMap<>();
        userInfoMap.put("userId", String.valueOf(user.getUserId()));
        userInfoMap.put("username", user.getUsername());
        userInfoMap.put("realName", user.getRealName());
        userInfoMap.put("email", user.getEmail());
        userInfoMap.put("phone", user.getPhone());
        userInfoMap.put("status", user.getStatus());
        userInfoMap.put("lastLoginIp", user.getLastLoginIp());
        userInfoMap.put("lastLoginTime", user.getLastLoginTime());

        // 构建返回数据
        java.util.Map<String, Object> responseData = new java.util.HashMap<>();
        responseData.put("accessToken", accessToken);
        responseData.put("refreshToken", refreshToken);
        responseData.put("userInfo", userInfoMap);
        responseData.put("roles", roles);

        response.getWriter().write(JSON.toJSONString(ResultUtils.success(Code.LOGIN_SUCCESS, responseData, "登录成功")));
        log.info("用户{}登录成功！Access Token 有效期7天，Refresh Token 有效期2小时", user.getUsername());
    }
}
