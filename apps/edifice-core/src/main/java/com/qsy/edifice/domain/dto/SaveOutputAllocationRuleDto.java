package com.qsy.edifice.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SaveOutputAllocationRuleDto {

    private BigDecimal employeePoolRate;
    private BigDecimal companyBaseRate;
    private List<StageRule> stages;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StageRule {
        private String stageName;
        private Integer stageOrder;
        private List<WorkRule> workRules;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WorkRule {
        private Integer workType;
        private BigDecimal workWeight;
        private BigDecimal projectCapRate;
    }
}
