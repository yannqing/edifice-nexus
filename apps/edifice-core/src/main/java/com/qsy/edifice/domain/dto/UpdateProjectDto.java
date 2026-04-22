package com.qsy.edifice.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 更新项目 DTO
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "更新项目请求参数")
public class UpdateProjectDto {

    /**
     * 项目id
     */
    @Schema(description = "项目id", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long projectId;

    /**
     * 项目名称
     */
    @Schema(description = "项目名称")
    private String projectName;

    /**
     * 项目编码
     */
    @Schema(description = "项目编码")
    private String projectCode;

    /**
     * 项目类型id
     */
    @Schema(description = "项目类型id")
    private Long projectType;

    /**
     * 项目状态：0-未开始/1-进行中/2-待验收/3-验收中/4-已结束
     */
    @Schema(description = "项目状态")
    private Integer projectStatus;

    /**
     * 合同类型：0-基本收费/1-基本+效益
     */
    @Schema(description = "合同类型")
    private Integer contractType;

    /**
     * 合同金额
     */
    @Schema(description = "合同金额")
    private BigDecimal contractAmount;

    /**
     * 基础金额
     */
    @Schema(description = "基础金额")
    private BigDecimal baseAmount;

    /**
     * 效益规则
     */
    @Schema(description = "效益规则")
    private String benefitRule;

    /**
     * 预计开始日期
     */
    @Schema(description = "预计开始日期")
    private LocalDateTime preStartTime;

    /**
     * 预计结束日期
     */
    @Schema(description = "预计结束日期")
    private LocalDateTime preEndTime;

    /**
     * 项目经理（用户id列表）
     */
    @Schema(description = "项目经理（用户id列表）")
    private List<Long> projectCharges;

    /**
     * 项目成员（用户id列表）
     */
    @Schema(description = "项目成员（用户id列表）")
    private List<Long> projectMembers;
}
