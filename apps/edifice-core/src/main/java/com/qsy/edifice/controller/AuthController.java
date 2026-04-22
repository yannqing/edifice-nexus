package com.qsy.edifice.controller;

import com.alibaba.fastjson2.JSON;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qsy.edifice.common.Code;
import com.qsy.edifice.domain.common.BaseResponse;
import com.qsy.edifice.domain.entity.SysRole;
import com.qsy.edifice.domain.entity.SysUser;
import com.qsy.edifice.mapper.SysUserMapper;
import com.qsy.edifice.utils.JwtUtils;
import com.qsy.edifice.utils.RedisCache;
import com.qsy.edifice.utils.ResultUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 鉴权相关接口：刷新 Token（登录/登出由 Spring Security filter 处理）
 */
@Slf4j
@Tag(name = "鉴权")
@RestController
@RequestMapping("/auth")
public class AuthController {

    @Resource
    private JwtUtils jwtUtils;

    @Resource
    private RedisCache redisCache;

    @Resource
    private SysUserMapper sysUserMapper;

    @PostMapping("/refresh")
    @Operation(summary = "刷新 Access Token", description = "凭合法 Refresh Token 换取新的 Access Token")
    public BaseResponse<Map<String, Object>> refresh(@RequestBody RefreshRequest body) {
        String refreshToken = body == null ? null : body.getRefreshToken();
        if (refreshToken == null || refreshToken.isEmpty()) {
            return ResultUtils.failure(Code.TOKEN_ERROR, null, "Refresh Token 不能为空");
        }

        // 1. 验证 Refresh Token 合法性
        try {
            jwtUtils.tokenVerify(refreshToken);
        } catch (com.auth0.jwt.exceptions.TokenExpiredException e) {
            return ResultUtils.failure(Code.REFRESH_TOKEN_EXPIRE, null, "Refresh Token 已过期，请重新登录");
        } catch (Exception e) {
            return ResultUtils.failure(Code.TOKEN_AUTHENTICATE_FAILURE, null, "非法 Refresh Token");
        }

        // 2. 验证 token 类型
        String type = JwtUtils.getTokenType(refreshToken);
        if (!"refresh".equals(type)) {
            return ResultUtils.failure(Code.TOKEN_AUTHENTICATE_FAILURE, null, "无效的 Token 类型，请使用 Refresh Token");
        }

        // 3. 验证 Redis 中存在（未被登出 / 未被强制下线）
        Object cached = redisCache.getCacheObject("refresh_token:" + refreshToken);
        if (cached == null) {
            return ResultUtils.failure(Code.REFRESH_TOKEN_EXPIRE, null, "Refresh Token 已失效，请重新登录");
        }

        // 4. 解析 userId，查询用户
        String userIdStr = jwtUtils.getUserIdFromRefreshToken(refreshToken);
        Long userId;
        try {
            userId = Long.parseLong(userIdStr);
        } catch (NumberFormatException e) {
            return ResultUtils.failure(Code.TOKEN_AUTHENTICATE_FAILURE, null, "Refresh Token 载荷异常");
        }

        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            return ResultUtils.failure(Code.TOKEN_AUTHENTICATE_FAILURE, null, "用户不存在");
        }

        // 5. 生成新的 Access Token（Refresh Token 保持不变直至到期）
        String userInfoJson;
        try {
            ObjectMapper mapper = new ObjectMapper();
            mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
            userInfoJson = mapper.writeValueAsString(user);
        } catch (Exception e) {
            log.error("序列化用户信息失败", e);
            return ResultUtils.failure(Code.FAILURE, null, "生成 Access Token 失败");
        }

        // 角色信息（当前简单实现：空数组；后续接入 sys_user_role 后填充）
        String rolesJson = JSON.toJSONString(new java.util.ArrayList<SysRole>());

        String newAccessToken = jwtUtils.generateAccessToken(userInfoJson, rolesJson);

        // 6. 失效旧的 Access Token、写入新的
        String userTokensJson = redisCache.getCacheObject("user_tokens:" + userId);
        if (userTokensJson != null) {
            com.alibaba.fastjson2.JSONObject userTokens = JSON.parseObject(userTokensJson);
            String oldAccessToken = userTokens.getString("accessToken");
            if (oldAccessToken != null) {
                redisCache.deleteObject("access_token:" + oldAccessToken);
            }
            userTokens.put("accessToken", newAccessToken);
            redisCache.setCacheObject("user_tokens:" + userId, userTokens.toJSONString(),
                    (int) (JwtUtils.REFRESH_TOKEN_TTL_MS / 1000), TimeUnit.SECONDS);
        }

        redisCache.setCacheObject("access_token:" + newAccessToken, userInfoJson,
                (int) (JwtUtils.ACCESS_TOKEN_TTL_MS / 1000), TimeUnit.SECONDS);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("accessToken", newAccessToken);
        data.put("expiresIn", JwtUtils.ACCESS_TOKEN_TTL_MS / 1000);

        log.info("用户 {} 刷新 Access Token 成功", user.getUsername());
        return ResultUtils.success(Code.SUCCESS, data, "刷新成功");
    }

    @lombok.Data
    public static class RefreshRequest {
        private String refreshToken;
    }
}
