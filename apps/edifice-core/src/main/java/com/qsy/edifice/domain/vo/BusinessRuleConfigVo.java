package com.qsy.edifice.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class BusinessRuleConfigVo {
    @JsonSerialize(using = ToStringSerializer.class)
    private Long ruleConfigId;
    private String bizType;
    private String bizTypeLabel;
    private String ruleKey;
    private String ruleName;
    private String ruleValue;
    private String valueType;
    private Integer enabled;
    private String description;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long updatedBy;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}
