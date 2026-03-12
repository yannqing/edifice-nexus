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
 * 项目类型实体类
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("project_type")
public class ProjectType implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 项目类型id
     */
    @TableId(value = "project_type_id", type = IdType.ASSIGN_ID)
    private Long projectTypeId;

    /**
     * 项目类型名称
     */
    @TableField("project_type_name")
    private String projectTypeName;

    /**
     * 项目类型唯一编码（项目编号）
     */
    @TableField("project_code")
    private String projectCode;

    /**
     * 项目类型状态：0-禁用/1-启用
     */
    @TableField("project_type_status")
    private Integer projectTypeStatus;

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
