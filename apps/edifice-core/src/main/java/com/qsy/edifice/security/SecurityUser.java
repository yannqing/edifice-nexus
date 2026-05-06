package com.qsy.edifice.security;

import com.qsy.edifice.domain.entity.SysRole;
import com.qsy.edifice.domain.entity.SysUser;
import lombok.Getter;
import lombok.Setter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

public class SecurityUser implements UserDetails {

    @Getter
    @Setter
    private List<SimpleGrantedAuthority> simpleGrantedAuthorities;

    @Getter
    @Setter
    private List<SysRole> sysRole;

    @Getter
    private final SysUser sysUser;

    public SecurityUser(SysUser sysUser) {
        this.sysUser = sysUser;
    }


    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return simpleGrantedAuthorities;
    }

    @Override
    public String getPassword() {
        if (sysUser.getOaAdminId() != null) {
            return "{edifice-oa}" + sysUser.getOaAdminId() + ":" + sysUser.getPassword();
        }
        return sysUser.getPassword();
    }

    @Override
    public String getUsername() {
        // 返回系统用户的用户名
        return sysUser.getUsername();
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        // 根据SysUser表结构，检查status字段（1-启用，0-禁用）
        return sysUser.getStatus() != null && sysUser.getStatus() == 1;
    }
}
