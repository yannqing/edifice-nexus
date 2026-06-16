package com.qsy.edifice.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 合同管理更新 DTO
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class UpdateContractDto {

    @Schema(description = "合同id")
    private Long contractId;

    @Schema(description = "合同名称")
    private String contractName;

    @Schema(description = "合同编号")
    private String contractCode;

    @Schema(description = "合同类型：0-基本收费/1-基本+效益")
    private Integer contractType;

    @Schema(description = "合同金额")
    private BigDecimal contractAmount;

    @Schema(description = "基本收费金额")
    private BigDecimal baseAmount;

    @Schema(description = "效益规则说明")
    private String benefitRules;

    @Schema(description = "预计效益金额")
    private BigDecimal benefitAmount;

    @Schema(description = "合同签订日期")
    private LocalDateTime signingDate;

    @Schema(description = "预计开始日期")
    private LocalDateTime preStartDate;

    @Schema(description = "预计结束日期")
    private LocalDateTime preEndDate;
}
