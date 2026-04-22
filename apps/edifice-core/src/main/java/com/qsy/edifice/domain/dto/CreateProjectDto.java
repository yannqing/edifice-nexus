package com.qsy.edifice.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 新建项目 DTO
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "新建项目请求参数")
public class CreateProjectDto {

    /**
     * 项目名称
     */
    @Schema(description = "项目名称", requiredMode = Schema.RequiredMode.REQUIRED)
    private String projectName;

    /**
     * 项目编码（如果为空，自动生成）
     */
    @Schema(description = "项目编码（如果为空，自动生成）")
    private String projectCode;

    /**
     * 项目类型id
     */
    @Schema(description = "项目类型id", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long projectType;

    /**
     * 合同类型：0-基本收费/1-基本+效益
     */
    @Schema(description = "合同类型：0-基本收费/1-基本+效益", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer contractType;

    /**
     * 合同金额
     */
    @Schema(description = "合同金额", requiredMode = Schema.RequiredMode.REQUIRED)
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
     * 合同主文件id（通过文件上传接口获取）
     */
    @Schema(description = "合同主文件id")
    private Long contractFile;

    /**
     * 合同其他附件id列表（通过文件上传接口获取）
     */
    @Schema(description = "合同其他附件id列表")
    private List<Long> contractOtherFiles;

    /**
     * 项目经理（用户id列表）
     */
    @Schema(description = "项目经理（用户id列表）", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<Long> projectCharges;

    /**
     * 项目成员（用户id列表）
     */
    @Schema(description = "项目成员（用户id列表）")
    private List<Long> projectMembers;

    /**
     * 签订日期
     */
    @Schema(description = "签订日期")
    private LocalDateTime signingTime;

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
}
