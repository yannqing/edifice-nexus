package com.qsy.edifice.domain.vo;

import lombok.Data;

@Data
public class ProjectTreeStatisticsVo {
    private long allCount;
    private long notStartedCount;
    private long processingCount;
    private long pendingAcceptanceCount;
    private long completedCount;
    private long archivedCount;
    private ProjectTreeSummaryVo summary;
}
