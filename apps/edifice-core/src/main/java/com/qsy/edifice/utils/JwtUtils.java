package com.qsy.edifice.utils;

import com.alibaba.fastjson.JSON;
import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qsy.edifice.domain.entity.SysUser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Date;
import java.util.List;

@Slf4j
public class JwtUtils {
    private static final String secret = "JWBigData-BackEnd";
    /**
     *  根据认证信息Authentication生成JWT token
     */
    public static String token(Authentication authentication){
        return JWT.create()
                .withExpiresAt(new Date(System.currentTimeMillis()+ 1000L * 60 * 60 * 24 * 3))  //设置过期时间:单位毫秒
                .withAudience(JSON.toJSONString(authentication)) //设置接受方信息，一般时登录用户
                .sign(Algorithm.HMAC256(secret));
    }

    /**
     * 根据用户详细信息，权限信息  生成token
     * @param userInfo 用户详细信息，密码为空
     * @param roles 用户角色
     * @return
     */

    public static String token(String userInfo, String roles){
        return JWT.create()
                .withExpiresAt(new Date(System.currentTimeMillis()+ 1000L * 60 * 60 * 24 * 3))  //设置过期时间:单位毫秒
                .withClaim("userInfo",userInfo)
                .withClaim("roles",roles)
                .sign(Algorithm.HMAC256(secret));
    }

    /**
     * 根据指定日期返回token
     * @param authentication 认证信息
     * @param time 过期时间 单位毫秒
     * @return 返回token
     */
    public static String token(Authentication authentication,Long time){
        return JWT.create()
                .withExpiresAt(new Date(System.currentTimeMillis()+ 1000L * 60 * 60 * 24 * 3))  //设置过期时间:单位毫秒
                .withAudience(JSON.toJSONString(authentication)) //设置接受方信息，一般时登录用户
                .sign(Algorithm.HMAC256(secret));

    }

    /**
     * 验证token合法性
     */
    public static void tokenVerify(String token){
        JWTVerifier jwtVerifier = JWT.require(Algorithm.HMAC256(secret)).build();
        jwtVerifier.verify(token);//没报错说明验证成功

//        log.info("token校验成功！");
    }

    /**
     * 刷新token
     * @param token
     * @return
     */
    public static String refreshToken(String token){
        JwtUtils.tokenVerify(token);
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return JwtUtils.token(authentication);
    }

    public static SysUser getUserFromToken(String token) throws JsonProcessingException {
        JWTVerifier jwtVerifier = JWT.require(Algorithm.HMAC256(secret)).build();
        DecodedJWT decodedJWT = jwtVerifier.verify(token);
        String userInfo =  decodedJWT.getClaim("userInfo").asString();

        // 配置ObjectMapper处理时间格式
        ObjectMapper objectMapper = new ObjectMapper();

        // 配置自定义的JavaTimeModule
        com.fasterxml.jackson.datatype.jsr310.JavaTimeModule javaTimeModule = new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule();
        javaTimeModule.addDeserializer(java.time.LocalDateTime.class,
            new com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer(
                java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        javaTimeModule.addDeserializer(java.time.LocalDate.class,
            new com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer(
                java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd")));

        objectMapper.registerModule(javaTimeModule);
        objectMapper.disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        // 添加调试日志
        log.info("Parsing userInfo from token: {}", userInfo);

        return objectMapper.readValue(userInfo, SysUser.class);
    }

    public static List<String> getUserAuthorizationFromToken(String token){
        try {
            JWTVerifier jwtVerifier = JWT.require(Algorithm.HMAC256(secret)).build();
            DecodedJWT decodedJWT = jwtVerifier.verify(token);
            return decodedJWT.getClaim("authList").asList(String.class);
        } catch (Exception e) {
            return null;
        }
    }

    // ==================== 双 Token 策略新增方法 ====================

    /**
     * 生成 Access Token（长期有效，用于日常API访问）
     * @param userInfo 用户详细信息JSON字符串
     * @param roles 用户角色JSON字符串
     * @return Access Token
     */
    public static String generateAccessToken(String userInfo, String roles) {
        return JWT.create()
                .withExpiresAt(new Date(System.currentTimeMillis() + 1000L * 60 * 60 * 24 * 7))  // 7天过期
                .withClaim("userInfo", userInfo)
                .withClaim("roles", roles)
                .withClaim("type", "access")  // 标记为 access token
                .sign(Algorithm.HMAC256(secret));
    }

    /**
     * 生成 Refresh Token（短期有效，仅用于刷新 Access Token）
     * @param userId 用户ID
     * @return Refresh Token
     */
    public static String generateRefreshToken(String userId) {
        return JWT.create()
                .withExpiresAt(new Date(System.currentTimeMillis() + 1000L * 60 * 60 * 2))  // 2小时过期
                .withClaim("userId", userId)
                .withClaim("type", "refresh")  // 标记为 refresh token
                .sign(Algorithm.HMAC256(secret));
    }

    /**
     * 从 token 中获取类型（access 或 refresh）
     * @param token JWT token
     * @return token 类型（"access" 或 "refresh"）
     */
    public static String getTokenType(String token) {
        try {
            DecodedJWT decodedJWT = JWT.decode(token);
            return decodedJWT.getClaim("type").asString();
        } catch (Exception e) {
            log.error("获取 token 类型失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 从 Refresh Token 中获取用户ID
     * @param refreshToken Refresh Token
     * @return 用户ID
     */
    public static String getUserIdFromRefreshToken(String refreshToken) {
        try {
            JWTVerifier jwtVerifier = JWT.require(Algorithm.HMAC256(secret)).build();
            DecodedJWT decodedJWT = jwtVerifier.verify(refreshToken);
            return decodedJWT.getClaim("userId").asString();
        } catch (Exception e) {
            log.error("从 Refresh Token 获取用户ID失败: {}", e.getMessage());
            return null;
        }
    }
}

