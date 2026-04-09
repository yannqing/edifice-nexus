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
 * 项目实体类
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("project")
public class Project implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 项目id
     */
    @TableId(value = "project_id", type = IdType.ASSIGN_ID)
    private Long projectId;

    /**
     * 项目名称
     */
    @TableField("project_name")
    private String projectName;

    /**
     * 项目唯一编码（项目编号）
     */
    @TableField("project_code")
    private String projectCode;

    /**
     * 类型id
     */
    @TableField("project_type")
    private Long projectType;

    /**
     * 项目状态：0-未开始/1-进行中/2-待验收/3-验收中/4-已结束
     */
    @TableField("project_status")
    private Integer projectStatus;

    /**
     * 是否公开：0-不公开/1-公开
     */
    @TableField("is_show")
    private Integer isShow;

    /**
     * 项目开始日期
     */
    @TableField("project_start_time")
    private LocalDateTime projectStartTime;

    /**
     * 项目结束日期
     */
    @TableField("project_end_time")
    private LocalDateTime projectEndTime;

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
