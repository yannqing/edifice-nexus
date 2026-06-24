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
 * 验工单实体类
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("inspection_form")
public class InspectionForm implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 验工单id
     */
    @TableId(value = "inspection_form_id", type = IdType.ASSIGN_ID)
    private Long inspectionFormId;

    /**
     * 验工单编号
     */
    @TableField("inspection_form_code")
    private String inspectionFormCode;

    /**
     * 项目id
     */
    @TableField("project_id")
    private String projectId;

    /**
     * 项目阶段id
     */
    @TableField("project_stage_id")
    private Long projectStageId;

    /**
     * 验工说明
     */
    @TableField("inspection_form_description")
    private String inspectionFormDescription;

    /**
     * 申请人id
     */
    @TableField("apply_user_id")
    private Long applyUserId;

    /**
     * 验工单状态：0-待审核/1-审核中/2-已驳回/3-已通过/4-草稿
     */
    @TableField("inspection_form_status")
    private Integer inspectionFormStatus;

    /**
     * 附件id（json数组）
     */
    @TableField("file_ids")
    private String fileIds;

    /**
     * 本次验工申请的完成比例（%，0-100）。历史数据默认 100。
     */
    @TableField("completion_ratio")
    private java.math.BigDecimal completionRatio;

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
