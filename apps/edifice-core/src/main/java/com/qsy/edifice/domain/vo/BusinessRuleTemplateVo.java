package com.qsy.edifice.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BusinessRuleTemplateVo {
    private String bizType;
    private String bizTypeLabel;
    private String ruleKey;
    private String ruleName;
    private String valueType;
    private String defaultValue;
    private String description;
}
