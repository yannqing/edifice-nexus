package com.qsy.edifice.domain.vo;

import com.qsy.edifice.domain.entity.SysRole;
import com.qsy.edifice.domain.entity.SysUser;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 登录成功后返回的封装信息
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginVo {

    private String userId;
    private String username;
    private String token;
    private List<SysRole> roles;

    public LoginVo(SysUser user, String token, List<SysRole> roles) {
        this.userId = String.valueOf(user.getUserId());
        this.username = user.getUsername();
        this.token = token;
        this.roles = roles;
    }

}
