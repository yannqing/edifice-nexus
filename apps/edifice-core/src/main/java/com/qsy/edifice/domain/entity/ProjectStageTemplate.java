package com.qsy.edifice.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 项目阶段模版实体类
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("project_stage_template")
public class ProjectStageTemplate implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 阶段id
     */
    @TableId(value = "stage_id", type = IdType.ASSIGN_ID)
    private Long stageId;

    /**
     * 阶段名称
     */
    @TableField("stage_name")
    private String stageName;

    /**
     * 关联项目类型id
     */
    @TableField("project_type_id")
    private Long projectTypeId;

    /**
     * 阶段默认产值比例
     */
    @TableField("stage_output")
    private BigDecimal stageOutput;

    /**
     * 阶段状态：0-禁用/1-启用
     */
    @TableField("stage_status")
    private Integer stageStatus;

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
