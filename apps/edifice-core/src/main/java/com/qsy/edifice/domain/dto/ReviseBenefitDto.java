package com.qsy.edifice.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 合同效益修正请求（v0.4）
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "合同效益修正参数")
public class ReviseBenefitDto {

    @Schema(description = "新的预计效益金额", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal newAmount;

    @Schema(description = "修正原因 / 说明")
    private String revisionReason;

    /** true 表示项目结算，置 contract.benefit_status=1，之后不可改 */
    @Schema(description = "是否最终确认（结算）")
    private Boolean isFinal;
}
