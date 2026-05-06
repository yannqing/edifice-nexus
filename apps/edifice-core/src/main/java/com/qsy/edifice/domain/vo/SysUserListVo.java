package com.qsy.edifice.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.qsy.edifice.domain.entity.SysUser;
import com.qsy.edifice.enums.ErrorType;
import com.qsy.edifice.exception.BusinessException;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.beans.BeanUtils;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SysUserListVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;

    private String username;

    private String employeeNo;

    private String realName;

    /** 0-男/1-女/2-其他 */
    private Integer gender;

    private String email;

    private String phone;

    private String avatar;

    private String position;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long departmentId;

    private String departmentName;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long positionId;

    private String positionName;

    private String professionalTitle;

    private LocalDate entryDate;

    /** 0-离职/1-在职 */
    private Integer employmentStatus;

    /** 账号状态：0禁用 1启用 */
    private Integer status;

    private LocalDateTime lastLoginTime;


    public static SysUserListVo objToVo(SysUser sysUser) {
        if (sysUser == null) {
            throw new BusinessException(ErrorType.SYSTEM_ERROR);
        }

        SysUserListVo sysUserListVo = new SysUserListVo();
        BeanUtils.copyProperties(sysUser, sysUserListVo);

        return sysUserListVo;
    }
}
