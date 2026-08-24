package com.qsy.edifice.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OutputAllocationRuleVo {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long ruleVersionId;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long projectTypeId;

    private String projectTypeCode;
    private String projectTypeName;
    private Integer versionNo;
    private BigDecimal employeePoolRate;
    private BigDecimal companyBaseRate;
    private LocalDateTime effectiveTime;
    private List<StageRuleVo> stages;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StageRuleVo {
        private String stageName;
        private Integer stageOrder;
        private BigDecimal stageOutput;
        private List<WorkRuleVo> workRules;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WorkRuleVo {
        private Integer workType;
        private String workTypeName;
        private BigDecimal workWeight;
        private BigDecimal projectCapRate;
    }
}
