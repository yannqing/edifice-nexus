package com.qsy.edifice.domain.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TodoCenterStatsVo {
    private Long pendingCount;
    private Long initiatedCount;
    private Long processedCount;
    private Long ccCount;
    private Long todayPendingCount;
}
