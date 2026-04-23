package com.qsy.edifice.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 回款顶部统计卡片 VO
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "回款统计VO")
public class CollectionStatisticsVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 应收款总额（元） */
    private BigDecimal totalExpected;

    /** 已回款金额（元） */
    private BigDecimal totalCollected;

    /** 待回款金额 = 应收 - 已收（元） */
    private BigDecimal totalPending;
}
