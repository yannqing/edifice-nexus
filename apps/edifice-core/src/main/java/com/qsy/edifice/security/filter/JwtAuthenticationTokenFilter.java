package com.qsy.edifice.security.filter;

import com.alibaba.fastjson2.JSON;
import com.qsy.edifice.common.Code;
import com.qsy.edifice.common.Constant;
import com.qsy.edifice.domain.entity.SysUser;
import com.qsy.edifice.utils.JwtUtils;
import com.qsy.edifice.utils.RedisCache;
import com.qsy.edifice.utils.ResultUtils;
import com.qsy.edifice.utils.Tools;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
public class JwtAuthenticationTokenFilter extends OncePerRequestFilter {


    private final RedisCache redisCache;

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

        String token = request.getHeader("token");
        //验证token的合法性，不报错即合法

        // 使用双 Token 策略：检查 access_token 前缀
        Object redisTokenObj = redisCache.getCacheObject("access_token:" + token);

        if (redisTokenObj == null) {
            response.setStatus(500);
            response.setContentType("application/json;charset=utf-8");
            response.getWriter().write(JSON.toJSONString(ResultUtils.failure(Code.TOKEN_EXPIRE, null, "Access Token 已过期，请使用 Refresh Token 刷新")));
            log.error("Access Token 已过期或不存在");
            return;
        }

        if (token!=null){
            try {
                //验证token的合法性，不抛异常则合法
                log.info("开始验证 Access Token: {}", token.substring(0, Math.min(50, token.length())) + "...");
                JwtUtils.tokenVerify(token);
                log.info("Access Token 格式验证通过");

                // 验证 Token 类型（确保是 Access Token）
                String tokenType = JwtUtils.getTokenType(token);
                if (!"access".equals(tokenType)) {
                    response.setStatus(403);
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write(JSON.toJSONString(ResultUtils.failure(Code.TOKEN_AUTHENTICATE_FAILURE, null, "无效的 Token 类型，请使用 Access Token")));
                    log.error("Token 类型错误：期望 access，实际 {}", tokenType);
                    return;
                }

                //从token中获取到用户的信息，以及对应用户的权限信息
                SysUser user = JwtUtils.getUserFromToken(token);
                log.info("成功从 Access Token 获取用户信息: {}", user.getUsername());

//                List<String> userAuthorization = JwtUtils.getUserAuthorizationFromToken(token);
//                List<SimpleGrantedAuthority> authorities = userAuthorization.stream().map(SimpleGrantedAuthority::new).collect(Collectors.toList());
                //放行后面的用户名密码过滤器
//                UsernamePasswordAuthenticationToken usernamePasswordAuthenticationToken = new UsernamePasswordAuthenticationToken(user,null,authorities);
                UsernamePasswordAuthenticationToken usernamePasswordAuthenticationToken = new UsernamePasswordAuthenticationToken(user,null,null);
                SecurityContextHolder.getContext().setAuthentication(usernamePasswordAuthenticationToken);
                log.info("JWT验证成功，用户: {}", user.getUsername());
            }catch (Exception e){
                response.setStatus(200);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write(JSON.toJSONString(ResultUtils.failure(Code.TOKEN_AUTHENTICATE_FAILURE,null,"非法 Access Token")));
                log.error("非法 Access Token({}) - 错误详情: {}", token, e.getMessage());
                return;
            }
        }
        filterChain.doFilter(request,response);
    }
}
