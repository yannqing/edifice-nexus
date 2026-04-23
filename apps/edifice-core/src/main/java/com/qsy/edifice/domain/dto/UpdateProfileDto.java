package com.qsy.edifice.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;

/**
 * 个人中心更新资料 DTO
 *
 * 仅包含员工本人可自助修改的字段，敏感/HR 管控字段（员工编号、身份证号、入职时间、合同期限、
 * 入社保时间、在职状态、离职时间、账号状态、用户名等）不允许通过该接口修改。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "个人中心更新资料请求")
public class UpdateProfileDto implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "真实姓名")
    private String realName;

    @Schema(description = "性别：0-男/1-女/2-其他")
    private Integer gender;

    @Schema(description = "民族")
    private String ethnicity;

    @Schema(description = "出生日期")
    private LocalDate birthDate;

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

    @Schema(description = "证书")
    private String certificates;

    @Schema(description = "户籍所在地")
    private String domicile;

    @Schema(description = "居住地")
    private String address;

    @Schema(description = "备注")
    private String remark;
}
