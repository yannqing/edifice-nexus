package com.qsy.edifice.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 项目统计VO
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ProjectStatisticsVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 项目总数
     */
    private Long totalCount;

    /**
     * 进行中的数量（项目状态=1）
     */
    private Long processingCount;

    /**
     * 待验收的数量（项目状态=2）
     */
    private Long pendingAcceptanceCount;

    /**
     * 已完成的数量（项目状态=4）
     */
    private Long completedCount;

    /**
     * 合同总额
     */
    private Double totalContractAmount;
}
