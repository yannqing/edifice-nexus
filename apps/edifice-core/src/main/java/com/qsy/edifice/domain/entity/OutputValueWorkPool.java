package com.qsy.edifice.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("output_value_work_pool")
public class OutputValueWorkPool {

    @TableId(value = "work_pool_id", type = IdType.AUTO)
    private Long workPoolId;

    @TableField("output_value_id")
    private Long outputValueId;

    @TableField("rule_version_id")
    private Long ruleVersionId;

    @TableField("rule_version_no")
    private Integer ruleVersionNo;

    @TableField("work_type")
    private Integer workType;

    @TableField("stage_work_ratio")
    private BigDecimal stageWorkRatio;

    @TableField("gross_rate")
    private BigDecimal grossRate;

    @TableField("gross_amount")
    private BigDecimal grossAmount;

    @TableField("project_rate")
    private BigDecimal projectRate;

    @TableField("project_amount")
    private BigDecimal projectAmount;

    @TableField("company_rate")
    private BigDecimal companyRate;

    @TableField("company_amount")
    private BigDecimal companyAmount;

    @TableField(value = "created_time", fill = FieldFill.INSERT)
    private LocalDateTime createdTime;

    @TableField(value = "updated_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedTime;

    @TableField("is_delete")
    @TableLogic
    private Integer isDelete;
}
