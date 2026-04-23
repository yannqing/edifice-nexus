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
import java.time.LocalDate;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "新增用户dto")
public class SysUserCreateDto implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "用户名", requiredMode = Schema.RequiredMode.REQUIRED)
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

    @Schema(description = "在职状态：0-离职/1-在职，默认 1")
    private Integer employmentStatus;

    @Schema(description = "户籍所在地")
    private String domicile;

    @Schema(description = "居住地")
    private String address;

    @Schema(description = "备注")
    private String remark;

    public static SysUser dtoToObj(SysUserCreateDto sysUserCreateDto) {
        if (sysUserCreateDto == null) {
            throw new BusinessException(ErrorType.SYSTEM_ERROR);
        }

        SysUser sysUser = new SysUser();
        BeanUtils.copyProperties(sysUserCreateDto, sysUser);
        return sysUser;
    }
}
