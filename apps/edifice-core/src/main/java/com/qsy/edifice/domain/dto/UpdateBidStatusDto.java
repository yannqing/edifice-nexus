package com.qsy.edifice.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * 更新投标业务状态（看板拖拽 / 状态按钮）
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "更新投标业务状态")
public class UpdateBidStatusDto {

    @Schema(description = "投标id", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long bidId;

    /** 0-筹备 / 1-已投递 / 2-中标 / 3-未中标 / 4-终止 */
    @Schema(description = "目标状态", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer bidStatus;

    /** 投递或得到结果时可以顺便更新日期 */
    @Schema(description = "投标日期")
    private LocalDate bidDate;

    @Schema(description = "结果日期")
    private LocalDate resultDate;
}
