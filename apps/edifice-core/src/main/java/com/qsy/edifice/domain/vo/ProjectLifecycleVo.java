package com.qsy.edifice.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 项目生命周期看板 VO
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ProjectLifecycleVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private ProjectDetailVo project;

    private ProjectArchiveVo archive;

    private ProjectArchiveDetailVo.ArchiveSummaryVo summary;

    private List<LifecycleStageVo> stages;

    private List<LifecycleEventVo> events;

    private List<ProjectFileVo> recentFiles;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class LifecycleStageVo implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        @JsonSerialize(using = ToStringSerializer.class)
        private Long projectStageId;

        private String stageName;

        private Integer stageStatus;

        private BigDecimal stageOutput;

        private BigDecimal benefitInclusionRatio;

        private Integer inspectionCount;

        private Integer latestInspectionStatus;

        private Integer outputValueCount;

        private BigDecimal paidOutputAmount;

        private BigDecimal collectionAmount;

        private Integer projectFileCount;

        private LocalDateTime latestActivityTime;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class LifecycleEventVo implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        private String eventId;

        private String eventType;

        private String eventTypeLabel;

        private String title;

        private String content;

        private Integer status;

        private String operatorName;

        private String link;

        private LocalDateTime occurredTime;
    }
}
