package com.qsy.edifice.domain.vo;


import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
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
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "用户详情vo")
public class SysUserDetailVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "用户id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;

    @Schema(description = "登录用户名")
    private String username;

    @Schema(description = "员工编号")
    private String employeeNo;

    @Schema(description = "真实姓名")
    private String realName;

    @Schema(description = "性别：0-男/1-女/2-其他")
    private Integer gender;

    @Schema(description = "民族")
    private String ethnicity;

    @Schema(description = "出生日期")
    private LocalDate birthDate;

    @Schema(description = "身份证号")
    private String idCard;

    @Schema(description = "邮箱")
    private String email;

    @Schema(description = "手机号")
    private String phone;

    @Schema(description = "头像URL")
    private String avatar;

    @Schema(description = "学历")
    private String education;

    @Schema(description = "毕业院校")
    private String school;

    @Schema(description = "专业")
    private String major;

    @Schema(description = "职务")
    private String position;

    @Schema(description = "部门ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long departmentId;

    @Schema(description = "部门名称")
    private String departmentName;

    @Schema(description = "岗位ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long positionId;

    @Schema(description = "岗位名称")
    private String positionName;

    @Schema(description = "职称")
    private String professionalTitle;

    @Schema(description = "证书")
    private String certificates;

    @Schema(description = "入职时间")
    private LocalDate entryDate;

    @Schema(description = "合同期限")
    private LocalDate contractEndDate;

    @Schema(description = "入社保时间")
    private LocalDate socialInsuranceDate;

    @Schema(description = "在职状态：0-离职/1-在职")
    private Integer employmentStatus;

    @Schema(description = "离职时间")
    private LocalDate resignDate;

    @Schema(description = "户籍所在地")
    private String domicile;

    @Schema(description = "居住地")
    private String address;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "账号状态：0-禁用/1-启用（能否登录）")
    private Integer status;

    @Schema(description = "最后登录ip")
    private String lastLoginIp;

    @Schema(description = "用户角色Vo")
    private SysRoleListVo userRole;

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
