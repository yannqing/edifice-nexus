package com.qsy.edifice.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 合同效益预测修正历史（v0.4）
 *
 * 每次合同 {@code benefit_amount} 变化都写一条；首次录入也写。
 * is_final=1 时合同 {@code benefit_status} 同步置 1，禁止再修改。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("contract_benefit_revision")
public class ContractBenefitRevision implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "revision_id", type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long revisionId;

    @TableField("contract_id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long contractId;

    /** 修正前金额（首次为空） */
    @TableField("old_amount")
    private BigDecimal oldAmount;

    @TableField("new_amount")
    private BigDecimal newAmount;

    /** 差额 = new - old（首次为空） */
    @TableField("delta_amount")
    private BigDecimal deltaAmount;

    @TableField("revision_reason")
    private String revisionReason;

    /** 是否最终确认（1=结算，与 contract.benefit_status 联动） */
    @TableField("is_final")
    private Integer isFinal;

    @TableField("operator_id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long operatorId;

    @TableField(value = "created_time", fill = FieldFill.INSERT)
    private LocalDateTime createdTime;

    @TableField(value = "updated_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedTime;

    @TableField("is_delete")
    @TableLogic
    private Integer isDelete;
}
