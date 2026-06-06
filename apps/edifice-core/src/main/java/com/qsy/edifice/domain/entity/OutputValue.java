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
@TableName("output_value")
public class OutputValue implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "output_value_id", type = IdType.ASSIGN_ID)
    private Long outputValueId;

    @TableField("project_id")
    private Long projectId;

    @TableField("project_stage_id")
    private Long projectStageId;

    @TableField("total_amount")
    private BigDecimal totalAmount;

    /** 0-待确认/1-待审核/2-已审批/3-已发放 */
    @TableField("status")
    private Integer status;

    @TableField("submit_user_id")
    private Long submitUserId;

    @TableField("confirm_user_id")
    private Long confirmUserId;

    @TableField("approve_user_id")
    private Long approveUserId;

    @TableField("pay_user_id")
    private Long payUserId;

    @TableField("current_handler_id")
    private Long currentHandlerId;

    @TableField("submit_time")
    private LocalDateTime submitTime;

    @TableField("approved_time")
    private LocalDateTime approvedTime;

    @TableField("paid_time")
    private LocalDateTime paidTime;

    /** 所属季度，格式 YYYY-Qn，如 2026-Q1 */
    @TableField("quarter")
    private String quarter;

    /** 公司留存金额（40%） */
    @TableField("company_reserve")
    private BigDecimal companyReserve;

    /** 领导额外金额（离职/降档差额） */
    @TableField("leader_extra")
    private BigDecimal leaderExtra;

    /** 其他金额（未发给离职成员等） */
    @TableField("other_amount")
    private BigDecimal otherAmount;

    /** 公司补贴（只记录，不计入产值） */
    @TableField("subsidy_amount")
    private BigDecimal subsidyAmount;

    /** 当前阶段应得（含基本+效益），v0.4 新增 */
    @TableField("stage_cumulative_amount")
    private BigDecimal stageCumulativeAmount;

    /** 历史字段：旧累计差额模型下的上一次累计；新单固定为 0 */
    @TableField("previous_cumulative_amount")
    private BigDecimal previousCumulativeAmount;

    /** 本期基本部分，v0.4 新增 */
    @TableField("base_amount_part")
    private BigDecimal baseAmountPart;

    /** 本期效益部分，v0.4 新增 */
    @TableField("benefit_amount_part")
    private BigDecimal benefitAmountPart;

    /** 快照：本单创建时合同的预计效益值，v0.4 新增 */
    @TableField("benefit_snapshot")
    private BigDecimal benefitSnapshot;

    @TableField(value = "created_time", fill = FieldFill.INSERT)
    private LocalDateTime createdTime;

    @TableField(value = "updated_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedTime;

    @TableField("is_delete")
    @TableLogic
    private Integer isDelete;
}
