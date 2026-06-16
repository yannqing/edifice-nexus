package com.qsy.edifice.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("business_rule_config")
public class BusinessRuleConfig {

    @TableId(value = "rule_config_id", type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long ruleConfigId;

    @TableField("biz_type")
    private String bizType;

    @TableField("rule_key")
    private String ruleKey;

    @TableField("rule_name")
    private String ruleName;

    @TableField("rule_value")
    private String ruleValue;

    @TableField("value_type")
    private String valueType;

    @TableField("enabled")
    private Integer enabled;

    @TableField("description")
    private String description;

    @TableField("updated_by")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long updatedBy;

    @TableField(value = "created_time", fill = FieldFill.INSERT)
    private LocalDateTime createdTime;

    @TableField(value = "updated_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedTime;

    @TableField("is_delete")
    @TableLogic
    private Integer isDelete;
}
