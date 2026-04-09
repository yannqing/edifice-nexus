package com.qsy.edifice.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 项目详情VO
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "项目详情vo")
public class ProjectDetailVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 项目id
     */
    @Schema(description = "项目id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long projectId;

    /**
     * 项目名称
     */
    @Schema(description = "项目名称")
    private String projectName;

    /**
     * 项目编码（如果为空，自动生成）
     */
    @Schema(description = "项目编码（如果为空，自动生成）")
    private String projectCode;

    /**
     * 项目类型
     */
    @Schema(description = "项目类型")
    private ProjectTypeVo projectType;

    /**
     * 项目状态：0-未开始/1-进行中/2-待验收/3-验收中/4-已结束
     */
    @Schema(description = "项目状态：0-未开始/1-进行中/2-待验收/3-验收中/4-已结束")
    private Integer projectStatus;

    /**
     * 当前项目阶段（最新）
     */
    @Schema(description = "当前项目阶段")
    private ProjectStageVo projectStage;

    /**
     * 所有项目阶段列表
     */
    @Schema(description = "所有项目阶段列表")
    private List<ProjectStageVo> projectStages;

    /**
     * 合同信息
     */
    @Schema(description = "合同信息")
    private ContractVo contract;

    /**
     * 项目成员列表
     */
    @Schema(description = "项目成员列表")
    private List<ProjectMemberVo> projectMemberList;

    /**
     * 预计开始时间
     */
    @Schema(description = "预计开始时间")
    private LocalDateTime preStartTime;

    /**
     * 预计结束时间
     */
    @Schema(description = "预计结束时间")
    private LocalDateTime preEndTime;
}
