package com.qsy.edifice.security.handler;

import com.alibaba.fastjson2.JSON;
import com.qsy.edifice.common.Code;
import com.qsy.edifice.utils.JwtUtils;
import com.qsy.edifice.utils.RedisCache;
import com.qsy.edifice.utils.ResultUtils;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component
public class MyLogoutSuccessHandler implements LogoutSuccessHandler {

    private final RedisCache redisCache;

    @Resource
    private JwtUtils jwtUtils;

    public MyLogoutSuccessHandler(RedisCache redisCache) {
        this.redisCache = redisCache;
    }

    /**
     * 退出成功处理器：删除redis中的token即可
     * @param request 会话请求
     * @param response 会话响应
     * @param authentication 认证信息
     * @throws IOException IO 异常
     */
    @Override
    public void onLogoutSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException {
        String token = request.getHeader("token");

        // 双 Token 策略：删除 Access Token 和 Refresh Token
        // 1. 删除 Access Token
        redisCache.deleteObject("access_token:" + token);

        // 2. 尝试从 token 获取用户ID，然后删除对应的 Refresh Token 和用户 token 映射
        try {
            com.qsy.edifice.domain.entity.SysUser user = jwtUtils.getUserFromToken(token);
            if (user != null && user.getUserId() != null) {
                // 获取用户 token 映射
                String userTokensJson = redisCache.getCacheObject("user_tokens:" + user.getUserId());
                if (userTokensJson != null) {
                    com.alibaba.fastjson2.JSONObject userTokens = JSON.parseObject(userTokensJson);
                    String refreshToken = userTokens.getString("refreshToken");

                    // 删除 Refresh Token
                    if (refreshToken != null) {
                        redisCache.deleteObject("refresh_token:" + refreshToken);
                        log.info("用户 {} 的 Refresh Token 已删除", user.getUserId());
                    }
                }

                // 删除用户 token 映射
                redisCache.deleteObject("user_tokens:" + user.getUserId());
                log.info("用户 {} 的所有 Token 已清除", user.getUserId());
            }
        } catch (Exception e) {
            log.warn("清除 Refresh Token 时出错（不影响登出）: {}", e.getMessage());
        }

        response.setStatus(200);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(JSON.toJSONString(ResultUtils.success(Code.LOGOUT_SUCCESS,null,"退出成功！")));
        log.info("退出成功!");
    }
}
