package com.qsy.edifice.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 项目归档详情 VO
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ProjectArchiveDetailVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private ProjectDetailVo project;

    private ProjectArchiveVo archive;

    private ArchiveSummaryVo summary;

    private List<ArchiveChecklistItemVo> checklist;

    private List<ArchiveInspectionVo> inspections;

    private List<ArchiveOutputValueVo> outputValues;

    private List<ArchiveCollectionVo> collections;

    private List<ProjectFileVo> projectFiles;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ArchiveSummaryVo implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        private BigDecimal contractAmount;
        private BigDecimal totalOutputAmount;
        private BigDecimal paidOutputAmount;
        private BigDecimal totalCollectionAmount;
        private Integer stageCount;
        private Integer completedStageCount;
        private Integer inspectionCount;
        private Integer outputValueCount;
        private Integer collectionCount;
        private Integer projectFileCount;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ArchiveChecklistItemVo implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        private String itemKey;
        private String itemName;
        private String status;
        private String description;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ArchiveInspectionVo implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        @JsonSerialize(using = ToStringSerializer.class)
        private Long inspectionFormId;
        private String inspectionFormCode;
        @JsonSerialize(using = ToStringSerializer.class)
        private Long projectStageId;
        private String stageName;
        private Integer inspectionFormStatus;
        private String applyUserName;
        private LocalDateTime createdTime;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ArchiveOutputValueVo implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        @JsonSerialize(using = ToStringSerializer.class)
        private Long outputValueId;
        @JsonSerialize(using = ToStringSerializer.class)
        private Long projectStageId;
        private String stageName;
        private String quarter;
        private BigDecimal totalAmount;
        private Integer status;
        private LocalDateTime submitTime;
        private LocalDateTime paidTime;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ArchiveCollectionVo implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        @JsonSerialize(using = ToStringSerializer.class)
        private Long collectionRecordId;
        @JsonSerialize(using = ToStringSerializer.class)
        private Long projectStageId;
        private String stageName;
        private BigDecimal amount;
        private LocalDate collectDate;
        private String recordUserName;
        private String remark;
    }
}
