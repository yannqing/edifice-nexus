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

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("output_value_adjustment_detail")
public class OutputValueAdjustmentDetail implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "adjustment_detail_id", type = IdType.ASSIGN_ID)
    private Long adjustmentDetailId;

    @TableField("output_value_id")
    private Long outputValueId;

    @TableField("source_output_value_id")
    private Long sourceOutputValueId;

    @TableField("source_project_stage_id")
    private Long sourceProjectStageId;

    @TableField("source_stage_name")
    private String sourceStageName;

    @TableField("source_base_ratio")
    private BigDecimal sourceBaseRatio;

    @TableField("source_benefit_ratio")
    private BigDecimal sourceBenefitRatio;

    @TableField("old_base_amount_snapshot")
    private BigDecimal oldBaseAmountSnapshot;

    @TableField("old_benefit_amount_snapshot")
    private BigDecimal oldBenefitAmountSnapshot;

    @TableField("old_stage_amount")
    private BigDecimal oldStageAmount;

    @TableField("new_base_amount_snapshot")
    private BigDecimal newBaseAmountSnapshot;

    @TableField("new_benefit_amount_snapshot")
    private BigDecimal newBenefitAmountSnapshot;

    @TableField("new_stage_amount")
    private BigDecimal newStageAmount;

    @TableField("already_adjusted_amount")
    private BigDecimal alreadyAdjustedAmount;

    @TableField("adjustment_amount")
    private BigDecimal adjustmentAmount;

    @TableField(value = "created_time", fill = FieldFill.INSERT)
    private LocalDateTime createdTime;

    @TableField(value = "updated_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedTime;

    @TableField("is_delete")
    @TableLogic
    private Integer isDelete;
}
