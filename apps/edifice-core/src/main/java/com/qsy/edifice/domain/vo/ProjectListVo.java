package com.qsy.edifice.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 项目列表VO
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ProjectListVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 项目id
     */
    private Long projectId;

    /**
     * 项目名称
     */
    private String projectName;

    /**
     * 项目编码（如果为空，自动生成）
     */
    private String projectCode;

    /**
     * 项目类型
     */
    private IdNameVo projectType;

    /**
     * 项目状态：0-未开始/1-进行中/2-待验收/3-验收中/4-已结束
     */
    private Integer projectStatus;

    /**
     * 项目阶段
     */
    private IdNameVo projectStage;

    /**
     * 合同金额
     */
    private Double contractAmount;

    /**
     * 项目成员列表（包含项目经理）
     */
    private List<ProjectMemberVo> projectMemberList;

    /**
     * 预计开始时间
     */
    private LocalDateTime preStartTime;

    /**
     * 预计结束时间
     */
    private LocalDateTime preEndTime;
}
