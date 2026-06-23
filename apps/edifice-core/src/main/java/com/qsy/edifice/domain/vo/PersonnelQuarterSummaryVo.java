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
 * 人员分配汇总表（季度维度）
 *
 * 只统计 status >= 2（已审批 / 已发放）的分配单。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "人员季度分配汇总")
public class PersonnelQuarterSummaryVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;

    private String realName;

    /** 本季参与的不同项目数 */
    private Integer projectCount;

    /** 应得金额（个人池 × 分配比例之和） */
    private BigDecimal allocAmount;

    /** 实得金额（应得 × 完成比例 或 离职归零之和） */
    private BigDecimal completionAmount;
}
