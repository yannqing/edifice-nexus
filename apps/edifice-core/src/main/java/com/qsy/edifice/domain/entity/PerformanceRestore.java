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
 * 绩效还原表（季度级）
 *
 * 每一行表示"某季度、某用户（可选某项目）应被还原的一笔金额"。
 * 财务按季度统一发起还原，完成后标记为已还原。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("performance_restore")
public class PerformanceRestore implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "restore_id", type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long restoreId;

    /** 季度，格式 YYYY-Qn，如 2026-Q1 */
    @TableField("quarter")
    private String quarter;

    @TableField("user_id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;

    /** 项目id（可空，整体还原时为空） */
    @TableField("project_id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long projectId;

    /** 还原金额 */
    @TableField("restore_amount")
    private BigDecimal restoreAmount;

    /** 0-待还原/1-已还原 */
    @TableField("status")
    private Integer status;

    /** 实际还原时间 */
    @TableField("restored_time")
    private LocalDateTime restoredTime;

    /** 操作人（财务） */
    @TableField("operator_id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long operatorId;

    @TableField("remark")
    private String remark;

    @TableField(value = "created_time", fill = FieldFill.INSERT)
    private LocalDateTime createdTime;

    @TableField(value = "updated_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedTime;

    @TableField("is_delete")
    @TableLogic
    private Integer isDelete;
}
