package com.qsy.edifice.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 项目成员实体类
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("project_member")
public class ProjectMember implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 项目成员id
     */
    @TableId(value = "project_member_id", type = IdType.ASSIGN_ID)
    private Long projectMemberId;

    /**
     * 项目id
     */
    @TableField("project_id")
    private Long projectId;

    /**
     * 用户id
     */
    @TableField("user_id")
    private Long userId;

    /**
     * 项目内角色id
     */
    @TableField("project_role")
    private Long projectRole;

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
