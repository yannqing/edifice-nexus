package com.qsy.edifice.domain.vo;

import lombok.Data;

@Data
public class InspectionOverviewVo {
    private Long pendingApproval;
    private Long pendingFirstReview;
    private Long approved;
    private Long rejected;
}