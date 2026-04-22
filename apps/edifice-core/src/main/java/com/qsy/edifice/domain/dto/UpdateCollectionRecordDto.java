package com.qsy.edifice.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "更新回款记录请求参数")
public class UpdateCollectionRecordDto {

    @Schema(description = "回款记录id", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long collectionRecordId;

    @Schema(description = "项目阶段id")
    private Long projectStageId;

    @Schema(description = "回款金额（元）")
    private BigDecimal amount;

    @Schema(description = "实际回款日期")
    private LocalDate collectDate;

    @Schema(description = "凭证文件id")
    private Long voucherFileId;

    @Schema(description = "备注")
    private String remark;
}
