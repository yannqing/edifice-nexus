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
@Schema(description = "创建回款记录请求参数")
public class CreateCollectionRecordDto {

    @Schema(description = "项目id", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long projectId;

    @Schema(description = "项目阶段id（可选）")
    private Long projectStageId;

    @Schema(description = "回款金额（元）", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal amount;

    @Schema(description = "实际回款日期", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDate collectDate;

    @Schema(description = "凭证文件id（可选）")
    private Long voucherFileId;

    @Schema(description = "备注")
    private String remark;
}
