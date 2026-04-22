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

    @TableField("user_id")
    private Long userId;

    /** 0-管理工作/1-基础工作/2-智励工作 */
    @TableField("work_type")
    private Integer workType;

    @TableField("ratio")
    private BigDecimal ratio;

    @TableField("amount")
    private Integer amount;

    @TableField(value = "created_time", fill = FieldFill.INSERT)
    private LocalDateTime createdTime;

    @TableField(value = "updated_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedTime;

    @TableField("is_delete")
    @TableLogic
    private Integer isDelete;
}
