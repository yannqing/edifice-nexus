package com.qsy.edifice.domain.vo;


import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ProjectStageVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 项目阶段id
     */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long projectStageId;

    /**
     * 项目id
     */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long projectId;

    /**
     * 阶段名称
     */
    private String stageName;

    /**
     * 阶段状态：0-未开始/1-进行中/2-待验收/3-已验收/4-已驳回/5-待分配/6-已完成
     */
    private Integer stageStatus;

    /**
     * 基本部分累计计入比例
     */
    private BigDecimal stageOutput;

    /**
     * 效益部分累计计入比例；为空时按 stageOutput 兜底
     */
    private BigDecimal benefitInclusionRatio;

    /**
     * 已审批通过的累计完成比例（%，0-100）
     */
    private BigDecimal completionRatio;

    /**
     * 阶段系数（默认 1.00）
     */
    private BigDecimal coefficient;

}
