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
@TableName("output_allocation_rule_item")
public class OutputAllocationRuleItem {

    @TableId(value = "rule_item_id", type = IdType.AUTO)
    private Long ruleItemId;

    @TableField("rule_version_id")
    private Long ruleVersionId;

    @TableField("stage_name")
    private String stageName;

    @TableField("stage_order")
    private Integer stageOrder;

    @TableField("work_type")
    private Integer workType;

    @TableField("work_weight")
    private BigDecimal workWeight;

    @TableField("project_cap_rate")
    private BigDecimal projectCapRate;

    @TableField(value = "created_time", fill = FieldFill.INSERT)
    private LocalDateTime createdTime;

    @TableField(value = "updated_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedTime;

    @TableField("is_delete")
    @TableLogic
    private Integer isDelete;
}
