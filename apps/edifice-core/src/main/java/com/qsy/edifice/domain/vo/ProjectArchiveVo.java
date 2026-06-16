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

/**
 * 项目归档 VO
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ProjectArchiveVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long projectId;

    private String projectName;

    private String projectCode;

    private ProjectTypeVo projectType;

    private Integer projectStatus;

    private ContractVo contract;

    private BigDecimal contractAmount;

    private Integer fileCount;

    private Integer completedStageCount;

    private Integer totalStageCount;

    private Boolean archiveReady;

    private String archiveWarning;

    private Integer archiveStatus;

    private LocalDateTime archiveTime;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long archiveUserId;

    private String archiveUserName;

    private String archiveRemark;

    private LocalDateTime projectStartTime;

    private LocalDateTime projectEndTime;

    private LocalDateTime updatedTime;
}
