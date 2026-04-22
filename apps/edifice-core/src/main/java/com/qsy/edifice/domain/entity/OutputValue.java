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

    @TableField("submit_time")
    private LocalDateTime submitTime;

    @TableField("approved_time")
    private LocalDateTime approvedTime;

    @TableField("paid_time")
    private LocalDateTime paidTime;

    @TableField(value = "created_time", fill = FieldFill.INSERT)
    private LocalDateTime createdTime;

    @TableField(value = "updated_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedTime;

    @TableField("is_delete")
    @TableLogic
    private Integer isDelete;
}
