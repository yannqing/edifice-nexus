package com.qsy.edifice.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 系统用户实体类
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("sys_user")
public class SysUser implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 用户id
     */
    @TableId(value = "user_id", type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;

    /**
     * 登录用户名
     */
    @TableField("username")
    private String username;

    /**
     * 加密密码
     */
    @TableField("password")
    private String password;

    /**
     * 员工编号（对应花名册"编号"）
     */
    @TableField("employee_no")
    private String employeeNo;

    /**
     * 真实姓名
     */
    @TableField("real_name")
    private String realName;

    /**
     * 性别：0-男/1-女/2-其他
     */
    @TableField("gender")
    private Integer gender;

    /**
     * 民族
     */
    @TableField("ethnicity")
    private String ethnicity;

    /**
     * 出生日期
     */
    @TableField("birth_date")
    private LocalDate birthDate;

    /**
     * 身份证号
     */
    @TableField("id_card")
    private String idCard;

    /**
     * 邮箱
     */
    @TableField("email")
    private String email;

    /**
     * 手机号
     */
    @TableField("phone")
    private String phone;

    /**
     * 头像URL
     */
    @TableField("avatar")
    private String avatar;

    /**
     * 学历
     */
    @TableField("education")
    private String education;

    /**
     * 毕业院校
     */
    @TableField("school")
    private String school;

    /**
     * 专业
     */
    @TableField("major")
    private String major;

    /**
     * 职务
     */
    @TableField("position")
    private String position;

    /**
     * 职称
     */
    @TableField("professional_title")
    private String professionalTitle;

    /**
     * 证书
     */
    @TableField("certificates")
    private String certificates;

    /**
     * 入职时间
     */
    @TableField("entry_date")
    private LocalDate entryDate;

    /**
     * 合同期限（到期日期）
     */
    @TableField("contract_end_date")
    private LocalDate contractEndDate;

    /**
     * 入社保时间
     */
    @TableField("social_insurance_date")
    private LocalDate socialInsuranceDate;

    /**
     * 在职状态：0-离职/1-在职
     */
    @TableField("employment_status")
    private Integer employmentStatus;

    /**
     * 离职时间
     */
    @TableField("resign_date")
    private LocalDate resignDate;

    /**
     * 户籍所在地
     */
    @TableField("domicile")
    private String domicile;

    /**
     * 居住地
     */
    @TableField("address")
    private String address;

    /**
     * 备注
     */
    @TableField("remark")
    private String remark;

    /**
     * 账号状态：0禁用 1启用（能否登录）
     */
    @TableField("status")
    private Integer status;

    /**
     * 最后登录ip
     */
    @TableField("last_login_ip")
    private String lastLoginIp;

    /**
     * 最后登录时间
     */
    @TableField("last_login_time")
    private LocalDateTime lastLoginTime;

    /**
     * 创建时间
     */
    @TableField(value = "created_time", fill = FieldFill.INSERT)
    private LocalDateTime createdTime;

    /**
     * 更新时间
     */
    @TableField(value = "updated_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedTime;

    /**
     * 逻辑删除
     */
    @TableField("is_delete")
    @TableLogic
    private Integer isDelete;
}