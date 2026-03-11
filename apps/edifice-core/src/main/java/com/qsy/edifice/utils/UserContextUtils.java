package com.qsy.edifice.utils;

import com.qsy.edifice.domain.entity.SysUser;
import com.qsy.edifice.security.SecurityUser;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 用户上下文工具类
 */
public class UserContextUtils {

    /**
     * 获取当前登录用户
     * @return 当前用户，如果未登录返回null
     */
    public static SysUser getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof SysUser) {
            // JWT过滤器直接将SysUser对象设置为principal
            return (SysUser) authentication.getPrincipal();
        } else if (authentication != null && authentication.getPrincipal() instanceof SecurityUser) {
            // 兼容SecurityUser类型的principal
            SecurityUser securityUser = (SecurityUser) authentication.getPrincipal();
            return securityUser.getSysUser();
        }
        return null;
    }

    /**
     * 获取当前登录用户ID
     * @return 用户ID，如果未登录返回null
     */
    public static Long getCurrentUserId() {
        SysUser user = getCurrentUser();
        return user != null ? user.getUserId() : null;
    }

    /**
     * 检查用户是否已登录
     * @return true-已登录，false-未登录
     */
    public static boolean isAuthenticated() {
        return getCurrentUser() != null;
    }
}