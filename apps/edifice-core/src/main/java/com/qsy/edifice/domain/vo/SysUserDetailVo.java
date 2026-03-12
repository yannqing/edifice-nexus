package com.qsy.edifice.domain.vo;


import com.qsy.edifice.domain.entity.SysRole;
import com.qsy.edifice.domain.entity.SysUser;
import com.qsy.edifice.enums.ErrorType;
import com.qsy.edifice.exception.BusinessException;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.beans.BeanUtils;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "用户详情vo")
public class SysUserDetailVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 用户id
     */
    @Schema(description = "用户id")
    private Long userId;

    /**
     * 登录用户名
     */
    @Schema(description = "登录用户名")
    private String username;

    /**
     * 真实姓名
     */
    @Schema(description = "真实姓名")
    private String realName;

    /**
     * 邮箱
     */
    @Schema(description = "邮箱")
    private String email;

    /**
     * 手机号
     */
    @Schema(description = "手机号")
    private String phone;

    /**
     * 状态：0禁用 1启用
     */
    @Schema(description = "状态：0禁用 1启用")
    private Integer status;

    /**
     * 最后登录ip
     */
    @Schema(description = "最后登录ip")
    private String lastLoginIp;

    /**
     * 用户角色Vo
     */
    @Schema(description = "用户角色Vo")
    private SysRoleListVo userRole;

    /**
     * 最后登录时间
     */
    @Schema(description = "最后登录时间")
    private LocalDateTime lastLoginTime;

    public static SysUserDetailVo objToVo(SysUser sysUser) {
        if (sysUser == null) {
            throw new BusinessException(ErrorType.SYSTEM_ERROR);
        }

        SysUserDetailVo sysUserDetailVo = new SysUserDetailVo();

        BeanUtils.copyProperties(sysUser, sysUserDetailVo);
        return sysUserDetailVo;
    }
}
