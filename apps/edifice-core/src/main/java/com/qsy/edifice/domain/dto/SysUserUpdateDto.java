package com.qsy.edifice.domain.dto;

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

@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "更新用户的请求")
public class SysUserUpdateDto implements Serializable {

    /**
     * 用户id
     */
    @Schema(description = "用户id", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long userId;

    /**
     * 登录用户名
     */
    @Schema(description = "用户名", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String username;

    /**
     * 真实姓名
     */
    @Schema(description = "真实姓名", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String realName;

    /**
     * 邮箱
     */
    @Schema(description = "邮箱", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String email;

    /**
     * 手机号
     */
    @Schema(description = "手机号", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String phone;

    @Serial
    private static final long serialVersionUID = 1L;

    public static SysUser dtoToObj(SysUserUpdateDto sysUserUpdateDto) {
        if (sysUserUpdateDto == null) {
            throw new BusinessException(ErrorType.SYSTEM_ERROR);
        }

        SysUser sysUser = new SysUser();
        BeanUtils.copyProperties(sysUserUpdateDto, sysUser);
        return sysUser;
    }
}
