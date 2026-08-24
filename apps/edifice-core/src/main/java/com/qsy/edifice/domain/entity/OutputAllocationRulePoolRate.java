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
@TableName("output_allocation_rule_pool_rate")
public class OutputAllocationRulePoolRate {

    @TableId(value = "pool_rate_id", type = IdType.AUTO)
    private Long poolRateId;

    @TableField("rule_version_id")
    private Long ruleVersionId;

    @TableField("work_type")
    private Integer workType;

    @TableField("gross_rate")
    private BigDecimal grossRate;

    @TableField("project_rate")
    private BigDecimal projectRate;

    @TableField("company_rate")
    private BigDecimal companyRate;

    @TableField(value = "created_time", fill = FieldFill.INSERT)
    private LocalDateTime createdTime;

    @TableField(value = "updated_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedTime;

    @TableField("is_delete")
    @TableLogic
    private Integer isDelete;
}
