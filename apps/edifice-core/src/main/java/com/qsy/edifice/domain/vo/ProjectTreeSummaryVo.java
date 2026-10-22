package com.qsy.edifice.domain.vo;

import lombok.Data;
import java.math.BigDecimal;

/** Totals for all matching records, before pagination, excluding context-only parents. */
@Data
public class ProjectTreeSummaryVo {
    private long totalCount;
    private long childCount;
    private long processingCount;
    private long pendingAcceptanceCount;
    private long completedCount;
    private BigDecimal rootContractAmount;
    private BigDecimal childContractAmount;
}
