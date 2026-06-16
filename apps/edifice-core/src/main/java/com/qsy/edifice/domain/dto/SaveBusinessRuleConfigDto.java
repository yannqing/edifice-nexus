package com.qsy.edifice.domain.dto;

import lombok.Data;

@Data
public class SaveBusinessRuleConfigDto {
    private Long ruleConfigId;
    private String bizType;
    private String ruleKey;
    private String ruleName;
    private String ruleValue;
    private String valueType;
    private Integer enabled;
    private String description;
}
