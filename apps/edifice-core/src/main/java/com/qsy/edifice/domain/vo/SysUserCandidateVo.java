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

/**
 * 用户精简 VO：用于业务场景（审批人选择、项目经理选择、项目成员选择等）的候选人下拉框。
 *
 * <p>与 {@link SysUserListVo} 的区别：只暴露选人必需的字段，
 * 不含手机号、邮箱、身份证、OA 账号、入职日期、最后登录时间等敏感/冗余信息。
 *
 * <p>权限要求：仅需登录（{@code isAuthenticated()}），
 * 而 {@code /users/all}（管理员全员查询）需要 {@code menu:user-management}。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class SysUserCandidateVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;

    private String username;

    private String realName;

    private String avatar;

    /** 手机号：用于业务页面的关键字搜索匹配（公司内部通讯场景可见） */
    private String phone;

    /** 主部门 id（用于按部门筛选场景） */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long departmentId;

    /** 主部门名称 */
    private String departmentName;

    /** 岗位 id */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long positionId;

    /** 岗位名称 */
    private String positionName;

    /** 0-离职/1-在职（业务侧通常只展示在职员工） */
    private Integer employmentStatus;

    /** 账号状态：0 禁用 1 启用 */
    private Integer status;

    public static SysUserCandidateVo objToVo(SysUser sysUser) {
        if (sysUser == null) {
            throw new BusinessException(ErrorType.SYSTEM_ERROR);
        }
        SysUserCandidateVo vo = new SysUserCandidateVo();
        BeanUtils.copyProperties(sysUser, vo);
        return vo;
    }
}
