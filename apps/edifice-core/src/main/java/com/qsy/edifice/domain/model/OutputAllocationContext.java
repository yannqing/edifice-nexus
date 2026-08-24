package com.qsy.edifice.domain.model;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@AllArgsConstructor
public class OutputAllocationContext {

    private Long ruleVersionId;
    private Integer ruleVersionNo;
    private BigDecimal employeePoolRate;
    private BigDecimal companyBaseRate;
    private BigDecimal employeePoolAmount;
    private BigDecimal companyBaseAmount;
    private BigDecimal workTransferAmount;
    private BigDecimal projectPoolAmount;
    private List<WorkPool> workPools;

    @Data
    @AllArgsConstructor
    public static class WorkPool {
        private Integer workType;
        private BigDecimal workWeight;
        private BigDecimal grossRate;
        private BigDecimal grossAmount;
        private BigDecimal projectRate;
        private BigDecimal projectAmount;
        private BigDecimal companyRate;
        private BigDecimal companyAmount;
    }
}
