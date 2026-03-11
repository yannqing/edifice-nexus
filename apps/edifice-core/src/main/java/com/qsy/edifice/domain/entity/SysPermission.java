package com.qsy.edifice.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 系统权限实体类
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("sys_permission")
public class SysPermission implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 权限id
     */
    @TableId(value = "permission_id", type = IdType.ASSIGN_ID)
    private Long permissionId;

    /**
     * 权限名称
     */
    @TableField("permission_name")
    private String permissionName;

    /**
     * 权限唯一编码
     */
    @TableField("permission_code")
    private String permissionCode;

    /**
     * 类型：1菜单 2按钮 3接口
     */
    @TableField("permission_type")
    private Integer permissionType;

    /**
     * 父级id
     */
    @TableField("parent_id")
    private Long parentId;

    /**
     * 前端路由或接口路径
     */
    @TableField("path")
    private String path;

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
