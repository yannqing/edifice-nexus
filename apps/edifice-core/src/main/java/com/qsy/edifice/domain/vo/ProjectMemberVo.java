package com.qsy.edifice.domain.vo;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 项目成员VO，用于列表展示
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ProjectMemberVo implements Serializable {
    //
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 项目成员id
     */
    private Long projectMemberId;

    /**
     * 项目id
     */
    private Long projectId;

    /**
     * 用户id
     */
    private Long userId;

    /**
     * 项目内角色id
     */
    private Long projectRoleId;

}
