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

    /** 旧口径分配比例（%），保留以兼容历史数据 */
    @TableField("ratio")
    private BigDecimal ratio;

    /** 新口径：分配比例（%） */
    @TableField("alloc_ratio")
    private BigDecimal allocRatio;

    /** 完成比例（%） */
    @TableField("completion_ratio")
    private BigDecimal completionRatio;

    /** 类型：0-员工正常/1-员工降档/2-领导兜底/3-公司留存/4-其他金额 */
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
