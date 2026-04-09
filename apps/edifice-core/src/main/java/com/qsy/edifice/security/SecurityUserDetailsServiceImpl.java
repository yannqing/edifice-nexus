package com.qsy.edifice.security;

import com.qsy.edifice.domain.entity.SysRole;
import com.qsy.edifice.domain.entity.SysUser;
import com.qsy.edifice.mapper.SysRoleMapper;
import com.qsy.edifice.mapper.SysUserMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class SecurityUserDetailsServiceImpl implements UserDetailsService {

    @Resource
    private SysUserMapper sysUserMapper;

    @Resource
    private SysRoleMapper sysRoleMapper;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        log.info("系统用户登录: {}", username);

        // 查询系统用户
        SysUser sysUser = sysUserMapper.selectByUsername(username);
        if (sysUser == null) {
            throw new UsernameNotFoundException("用户不存在: " + username);
        }

        log.info("找到系统用户: {}", sysUser.getUsername());

        // 查询用户角色
        List<SysRole> roles = sysRoleMapper.selectRolesByUserId(sysUser.getUserId());
        log.info("用户 {} 的角色: {}", sysUser.getUsername(), roles.size());

        // 构建 SecurityUser
        SecurityUser securityUser = new SecurityUser(sysUser);
        securityUser.setSysRole(roles);

        // 构建权限列表
        List<SimpleGrantedAuthority> authorities = roles.stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.getRoleCode()))
                .collect(Collectors.toList());

        // 添加权限
        List<String> permissionCodes = sysUserMapper.selectPermissionCodesByUserId(sysUser.getUserId());
        authorities.addAll(permissionCodes.stream()
                .map(SimpleGrantedAuthority::new)
                .toList());

        // 设置权限
        securityUser.setSimpleGrantedAuthorities(authorities);

        return securityUser;
    }
}
