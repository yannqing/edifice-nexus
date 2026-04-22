package com.qsy.edifice.security.filter;

import com.alibaba.fastjson2.JSON;
import com.qsy.edifice.common.Code;
import com.qsy.edifice.common.Constant;
import com.qsy.edifice.domain.entity.SysUser;
import com.qsy.edifice.utils.JwtUtils;
import com.qsy.edifice.utils.RedisCache;
import com.qsy.edifice.utils.ResultUtils;
import com.qsy.edifice.utils.Tools;
import jakarta.annotation.Resource;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@Component
public class JwtAuthenticationTokenFilter extends OncePerRequestFilter {


    private final RedisCache redisCache;

    @Resource
    private JwtUtils jwtUtils;

    public JwtAuthenticationTokenFilter(RedisCache redisCache) {
        this.redisCache = redisCache;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        //匿名地址，直接放行
        String requestURI = request.getRequestURI();
        if (Tools.contains(requestURI, Constant.anonymousConstant) || Constant.isMatch(requestURI)) {
            filterChain.doFilter(request,response);
            return;
        }

        // 优先从请求头获取 token，其次从 query 参数获取（用于文件下载等场景）
        String token = request.getHeader("token");
        if (token == null || token.isEmpty()) {
            token = request.getParameter("token");
        }
        //验证token的合法性，不报错即合法

        // Token 未携带
        if (token == null || token.isEmpty()) {
            writeJson(response, Code.TOKEN_ERROR, "未携带 Access Token，请先登录");
            log.warn("请求未携带 token: {}", requestURI);
            return;
        }

        // 使用双 Token 策略：检查 access_token 前缀
        Object redisTokenObj = redisCache.getCacheObject("access_token:" + token);

        if (redisTokenObj == null) {
            // 统一用 ACCESS_TOKEN_EXPIRE(10003) + HTTP 200，前端据此跳转登录
            writeJson(response, Code.ACCESS_TOKEN_EXPIRE, "Access Token 已过期，请使用 Refresh Token 刷新");
            log.info("Access Token 已过期或不存在");
            return;
        }

        try {
            // 验证 token 签名与有效期
            jwtUtils.tokenVerify(token);

            // 验证 Token 类型（确保是 Access Token）
            String tokenType = JwtUtils.getTokenType(token);
            if (!"access".equals(tokenType)) {
                writeJson(response, Code.TOKEN_AUTHENTICATE_FAILURE, "无效的 Token 类型，请使用 Access Token");
                log.warn("Token 类型错误：期望 access，实际 {}", tokenType);
                return;
            }

            // 从token中获取用户信息
            SysUser user = jwtUtils.getUserFromToken(token);

            UsernamePasswordAuthenticationToken authToken =
                    new UsernamePasswordAuthenticationToken(user, null, null);
            SecurityContextHolder.getContext().setAuthentication(authToken);
            log.debug("JWT 验证成功，用户: {}", user.getUsername());
        } catch (com.auth0.jwt.exceptions.TokenExpiredException e) {
            writeJson(response, Code.ACCESS_TOKEN_EXPIRE, "Access Token 已过期，请使用 Refresh Token 刷新");
            log.info("Access Token 已过期: {}", e.getMessage());
            return;
        } catch (Exception e) {
            writeJson(response, Code.TOKEN_AUTHENTICATE_FAILURE, "非法 Access Token");
            log.error("非法 Access Token - 错误详情: {}", e.getMessage());
            return;
        }

        filterChain.doFilter(request, response);
    }

    /**
     * 统一的 JSON 响应：HTTP 200 + code 语义化，避免前端看到 5xx 直接抛错
     */
    private void writeJson(HttpServletResponse response, Integer code, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(JSON.toJSONString(ResultUtils.failure(code, null, message)));
    }
}
