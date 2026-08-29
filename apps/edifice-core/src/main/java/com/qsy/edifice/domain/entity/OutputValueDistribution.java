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
@TableName("output_value_distribution")
public class OutputValueDistribution implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "distribution_id", type = IdType.ASSIGN_ID)
    private Long distributionId;

    @TableField("output_value_id")
    private Long outputValueId;

    /** 0-当前阶段正常分配/1-历史效益补差扣回 */
    @TableField("component_type")
    private Integer componentType;

    @TableField("source_distribution_id")
    private Long sourceDistributionId;

    @TableField("source_output_value_id")
    private Long sourceOutputValueId;

    @TableField("source_project_stage_id")
    private Long sourceProjectStageId;

    @TableField("user_id")
    private Long userId;

    /** 0-管理工作/1-基础工作/2-智励工作 */
    @TableField("work_type")
    private Integer workType;

    /** 旧口径分配比例（%），保留以兼容历史数据 */
    @TableField("ratio")
    private BigDecimal ratio;

    /** 新口径：分配比例（%） */
    @TableField("alloc_ratio")
    private BigDecimal allocRatio;

    /** 完成比例（%） */
    @TableField("completion_ratio")
    private BigDecimal completionRatio;

    /** allocation_v2：关联的工作类型资金池 */
    @TableField("work_pool_id")
    private Long workPoolId;

    /** allocation_v2：对应工作类型资金池内的人员比例 */
    @TableField("role_alloc_ratio")
    private BigDecimal roleAllocRatio;

    /** allocation_v2：兑现前计划金额 */
    @TableField("planned_amount")
    private BigDecimal plannedAmount;

    /** allocation_v2：兑现不足或离职转公司金额 */
    @TableField("company_delta")
    private BigDecimal companyDelta;

    @TableField("adjustment_target_amount")
    private BigDecimal adjustmentTargetAmount;

    @TableField("previous_adjusted_amount")
    private BigDecimal previousAdjustedAmount;

    @TableField("remaining_adjustment_amount")
    private BigDecimal remainingAdjustmentAmount;

    /** 类型：0-员工正常/1-员工降档/2-领导兜底/3-公司留存/4-其他金额/5-效益补差扣回 */
    @TableField("dist_type")
    private Integer distType;

    /** 下单时成员是否在职：0-离职/1-在职 */
    @TableField("is_active")
    private Integer isActive;

    @TableField("amount")
    private BigDecimal amount;

    @TableField(value = "created_time", fill = FieldFill.INSERT)
    private LocalDateTime createdTime;

    @TableField(value = "updated_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedTime;

    @TableField("is_delete")
    @TableLogic
    private Integer isDelete;
}
