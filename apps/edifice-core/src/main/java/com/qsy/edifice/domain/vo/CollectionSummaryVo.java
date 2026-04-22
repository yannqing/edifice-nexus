package com.qsy.edifice.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 项目级别的回款汇总 VO（用于回款列表页）
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "项目回款汇总VO")
public class CollectionSummaryVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long projectId;

    private String projectName;
    private String projectCode;

    /** 项目分类名称（A类 / B类 ...） */
    private String projectTypeCode;
    private String projectTypeName;

    /** 项目经理姓名（首个） */
    private String managerName;

    /** 合同金额（元） */
    private Integer contractAmount;

    /** 已完成阶段的描述，如 "1-3" */
    private String completedPhases;

    /** 应收金额 = 合同金额 × 已完成阶段产值比例之和（元） */
    private Integer expectedAmount;

    /** 已回款金额（元） */
    private Integer collectedAmount;

    /** 回款率 (%), 保留 1 位小数 */
    private BigDecimal collectionRate;

    /** 状态：0-未回款/1-部分回款/2-已回款 */
    private Integer collectionStatus;
}
