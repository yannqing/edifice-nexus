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
 * 项目阶段实体类
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("project_stage")
public class ProjectStage implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 项目阶段id
     */
    @TableId(value = "project_stage_id", type = IdType.ASSIGN_ID)
    private Long projectStageId;

    /**
     * 项目id
     */
    @TableField("project_id")
    private Long projectId;

    /**
     * 阶段名称
     */
    @TableField("stage_name")
    private String stageName;

    /**
     * 阶段状态：0-未开始/1-进行中/2-待验收/3-已验收/4-已驳回/5-待分配/6-已完成
     */
    @TableField("stage_status")
    private Integer stageStatus;

    /**
     * 阶段产值比例
     */
    @TableField("stage_output")
    private BigDecimal stageOutput;

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
