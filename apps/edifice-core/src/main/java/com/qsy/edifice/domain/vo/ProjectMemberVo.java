package com.qsy.edifice.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 项目成员VO，用于列表展示
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ProjectMemberVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 用户id
     */
    private Long userId;

    /**
     * 用户名称（真实姓名）
     */
    private String userName;

    /**
     * 项目角色id
     */
    private Long projectRoleId;

    /**
     * 项目角色名称
     */
    private String projectRoleName;
}
