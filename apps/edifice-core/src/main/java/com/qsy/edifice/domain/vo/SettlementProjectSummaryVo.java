package com.qsy.edifice.domain.vo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class SettlementProjectSummaryVo {
    private Long projectCount = 0L;
    private Long completedCount = 0L;
    private BigDecimal contractAmount = BigDecimal.ZERO;
    private BigDecimal baseAmount = BigDecimal.ZERO;
    private BigDecimal benefitAmount = BigDecimal.ZERO;
}
